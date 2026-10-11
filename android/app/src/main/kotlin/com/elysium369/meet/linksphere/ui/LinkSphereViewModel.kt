package com.elysium369.meet.linksphere.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.linksphere.data.LinkSphereRepository
import com.elysium369.meet.linksphere.domain.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel para Elysium LinkSphere.
 * Maneja el estado reactivo del feed, filtros por pestaña y categoría,
 * búsqueda de contenido y creación de nuevas cápsulas de enlace.
 */
class LinkSphereViewModel(application: Application) : AndroidViewModel(application) {

    val repository = LinkSphereRepository(application.applicationContext)

    private val _activeTab = MutableStateFlow(LinkFeedTab.FOR_YOU)
    val activeTab: StateFlow<LinkFeedTab> = _activeTab.asStateFlow()

    private val _selectedCategory = MutableStateFlow(LinkCategory.ALL)
    val selectedCategory: StateFlow<LinkCategory> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val playbackPreference: StateFlow<PlaybackPreference> = repository.playbackPrefFlow

    val feedPosts: StateFlow<List<LinkPost>> = combine(
        repository.postsFlow,
        _activeTab,
        _selectedCategory,
        _searchQuery,
    ) { posts, tab, category, query ->
        var filtered = posts

        // Filtro por Tab
        filtered = when (tab) {
            LinkFeedTab.FOR_YOU -> filtered
            LinkFeedTab.FOLLOWING -> filtered.filter { it.isAuthorFollowed }
            LinkFeedTab.TRENDING -> filtered.sortedByDescending { it.reactionsCount + it.savesCount * 2 }
            LinkFeedTab.CATEGORIES -> {
                if (category != LinkCategory.ALL) filtered.filter { it.category == category }
                else filtered
            }
        }

        // Filtro por Categoría adicional si no está en tab CATEGORIES
        if (tab != LinkFeedTab.CATEGORIES && category != LinkCategory.ALL) {
            filtered = filtered.filter { it.category == category }
        }

        // Filtro por Búsqueda
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            filtered = filtered.filter { post ->
                post.metadata.title.lowercase().contains(q) ||
                        post.userCaption.lowercase().contains(q) ||
                        post.authorName.lowercase().contains(q) ||
                        post.authorHandle.lowercase().contains(q) ||
                        post.link.provider.displayName.lowercase().contains(q) ||
                        post.tags.any { it.lowercase().contains(q) }
            }
        }

        filtered
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setTab(tab: LinkFeedTab) {
        _activeTab.value = tab
    }

    fun setCategory(category: LinkCategory) {
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleReaction(postId: String, reactionType: LinkReactionType) {
        repository.toggleReaction(postId, reactionType)
    }

    fun toggleSave(postId: String) {
        repository.toggleSave(postId)
    }

    fun toggleFollow(authorId: String) {
        repository.toggleFollow(authorId)
    }

    fun addComment(postId: String, text: String) {
        if (text.isNotBlank()) {
            repository.addComment(postId, text)
        }
    }

    fun setPlaybackPreference(pref: PlaybackPreference) {
        repository.setPlaybackPreference(pref)
    }

    fun publishCapsule(
        url: String,
        caption: String,
        category: LinkCategory,
        tags: List<String> = emptyList(),
        onSuccess: (LinkPost) -> Unit,
    ) {
        viewModelScope.launch {
            val resolvedLink = SafeLinkResolver.resolve(url)
            val post = repository.createPost(
                link = resolvedLink,
                userCaption = caption,
                category = category,
                tags = tags,
            )
            onSuccess(post)
        }
    }
}
