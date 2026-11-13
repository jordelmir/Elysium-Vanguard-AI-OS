package com.elysium.vanguard.core.search

import java.io.File
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How the search results are ordered.
 */
enum class SearchSort { RELEVANCE, RECENT, ALPHABETICAL }

/**
 * GLOBAL SEARCH ENGINE
 *
 * A recursive, streaming search over the filesystem that a desktop file manager
 * would offer:
 *
 *  - the query understands the filter DSL (`ext:`, `size:>1MB`, `type:video`,
 *    `modified:last_week`, `name:` and `name~:` for regular expressions)
 *  - the free text part is matched with the fuzzy engine, so "elmg" finds
 *    "elamigos-1.bin"
 *  - results are ranked by relevance, then by how close they are to the root
 *  - results stream in while the walk is still running, with a live counter
 *  - the walk is cancellable and bounded in depth and in results
 */
@Singleton
class GlobalSearchEngine @Inject constructor(
    private val fuzzy: FuzzySearchEngine,
    private val filterParser: FileFilterParser
) {

    data class Hit(
        val file: File,
        val score: Double,
        val matchedIndices: IntArray,
        val relativePath: String
    ) {
        override fun equals(other: Any?): Boolean =
            other is Hit && other.file.absolutePath == file.absolutePath

        override fun hashCode(): Int = file.absolutePath.hashCode()
    }

    data class Progress(
        val scannedFiles: Int,
        val scannedDirectories: Int,
        val hits: Int,
        val elapsedMs: Long,
        val finished: Boolean
    )

    data class Result(
        val hits: List<Hit>,
        val scannedFiles: Int,
        val scannedDirectories: Int,
        val elapsedMs: Long,
        val truncated: Boolean
    )

    /**
     * Walks [roots] looking for [query].
     *
     * @param onHit invoked for every match, as soon as it is found.
     * @param onProgress invoked periodically with the live counters.
     * @param isCancelled polled between entries so the caller can stop the walk.
     */
    fun search(
        query: String,
        roots: List<File>,
        maxResults: Int = 400,
        maxDepth: Int = 16,
        onHit: (Hit) -> Unit,
        onProgress: (Progress) -> Unit,
        isCancelled: () -> Boolean = { false }
    ): Result {
        val started = (System.nanoTime() / 1_000_000L)
        val filter = filterParser.parse(query)
        val textQuery = query
            .trim()
            .split(' ')
            .filter { it.isNotBlank() && !it.contains(':') && !it.startsWith("size") && !it.startsWith("type") && !it.startsWith("ext") }
            .joinToString(" ")
            .trim()

        val hits = mutableListOf<Hit>()
        val seen = HashSet<String>()
        var scannedFiles = 0
        var scannedDirectories = 0
        var truncated = false

        val queue = ArrayDeque<Pair<File, Int>>()
        roots.filter { it.exists() }.forEach { root ->
            queue.add(root to 0)
        }

        while (queue.isNotEmpty()) {
            if (isCancelled()) break

            val (directory, depth) = queue.removeFirst()

            val children = try {
                directory.listFiles()
            } catch (_: Exception) {
                null
            } ?: continue

            scannedDirectories++

            for (child in children) {
                if (isCancelled()) break
                if (child.isDirectory) {
                    // BUG FIX: a directory is a legitimate result. Searching for a
                    // folder name used to return nothing at all.
                    if (matchesName(child, textQuery, filter)) {
                        val scored = if (textQuery.isEmpty()) {
                            null
                        } else {
                            fuzzy.score(textQuery, child.name, minScore = 0.30)
                        }
                        if (scored != null || textQuery.isEmpty()) {
                            val relative = relativise(child, roots)
                            if (seen.add(child.absolutePath)) {
                                hits += Hit(
                                    file = child,
                                    score = relevance(textQuery, child.name, scored?.score ?: 0.0),
                                    matchedIndices = scored?.matchedIndices ?: IntArray(0),
                                    relativePath = relative
                                )
                                onHit(hits.last())
                            }
                        }
                    }
                    if (depth < maxDepth && !isSkippedDirectory(child)) {
                        queue.add(child to depth + 1)
                    }
                    continue
                }

                scannedFiles++

                // The name is matched by the fuzzy engine, so the substring
                // constraint of the filter must not reject fuzzy matches.
                if (!filterParser.matches(child, filter, ignoreName = true)) continue

                val scored = if (textQuery.isEmpty()) {
                    null
                } else {
                    fuzzy.score(textQuery, child.name, minScore = 0.30) ?: continue
                }
                val score = relevance(textQuery, child.name, scored?.score ?: 0.0)
                val relative = relativise(child, roots)

                if (seen.add(child.absolutePath)) {
                    hits += Hit(child, score, scored?.matchedIndices ?: IntArray(0), relative)
                    onHit(hits.last())
                }

                if (hits.size >= maxResults) {
                    truncated = true
                    break
                }
            }

            if (scannedFiles % 256 == 0) {
                onProgress(
                    Progress(
                        scannedFiles = scannedFiles,
                        scannedDirectories = scannedDirectories,
                        hits = hits.size,
                        elapsedMs = (System.nanoTime() / 1_000_000L) - started,
                        finished = false
                    )
                )
            }

            if (truncated) break
        }

        val ranked = hits.sortedWith(
            compareByDescending<Hit> { it.score }
                .thenBy { it.relativePath.count { ch -> ch == '/' } }
                .thenBy { it.file.name.length }
                .thenBy { it.relativePath }
        )

        onProgress(
            Progress(
                scannedFiles = scannedFiles,
                scannedDirectories = scannedDirectories,
                hits = ranked.size,
                elapsedMs = (System.nanoTime() / 1_000_000L) - started,
                finished = true
            )
        )

        return Result(
            hits = ranked,
            scannedFiles = scannedFiles,
            scannedDirectories = scannedDirectories,
            elapsedMs = (System.nanoTime() / 1_000_000L) - started,
            truncated = truncated
        )
    }

    companion object {
        /**
         * Orders the hits the way the user asked.
         *
         * RELEVANCE keeps the ranking computed by the walk, RECENT puts the newest
         * files first and ALPHABETICAL sorts by name.
         */
        fun sort(hits: List<Hit>, sort: SearchSort): List<Hit> = when (sort) {
            SearchSort.RELEVANCE -> hits
            SearchSort.RECENT -> hits.sortedByDescending { it.file.lastModified() }
            SearchSort.ALPHABETICAL -> hits.sortedBy { it.file.name.lowercase() }
        }
    }

    /**
     * True when the name of [file] satisfies the free text part of the query.
     */
    private fun matchesName(
        file: File,
        textQuery: String,
        filter: FileFilterParser.Filter
    ): Boolean {
        if (textQuery.isNotEmpty()) {
            val hits = fuzzy.score(textQuery, file.name, minScore = 0.30) ?: return false
            @Suppress("UNUSED_EXPRESSION")
            hits
        }
        // The remaining constraints still apply to folders.
        return filterParser.matches(file, filter, ignoreName = true)
    }

    /**
     * Composite relevance.
     *
     * A pure fuzzy score lets scattered matches win over real ones, so the
     * ranking is layered the way a desktop search engine does it:
     *
     *   exact name  >  name prefix  >  name contains  >  fuzzy score
     */
    private fun relevance(query: String, name: String, fuzzyScore: Double): Double {
        if (query.isEmpty()) return 1.0
        val q = query.lowercase()
        val n = name.lowercase()
        return when {
            q == n -> 10_000.0
            n.startsWith(q) -> 9_000.0 - (n.length - q.length) * 0.1
            n.contains(q) -> 8_000.0 - n.indexOf(q) * 10.0 - (n.length - q.length) * 0.1
            else -> fuzzyScore * 100.0
        }
    }

    private fun relativise(file: File, roots: List<File>): String {
        roots.forEach { root ->
            val base = root.absolutePath
            if (file.absolutePath.startsWith(base)) {
                return file.absolutePath.removePrefix(base).trimStart('/')
            }
        }
        return file.absolutePath
    }

    /**
     * Folders that never contain user searchable content and are expensive to
     * walk. Skipping them is what keeps a device wide search responsive.
     */
    private fun isSkippedDirectory(directory: File): Boolean {
        val name = directory.name
        if (name == ".thumbnails" || name == ".trash" || name == ".trashed") return true
        if (name == "cache" || name == ".cache") return true
        if (name == "node_modules") return true
        if (name.startsWith(".git")) return true
        return false
    }
}
