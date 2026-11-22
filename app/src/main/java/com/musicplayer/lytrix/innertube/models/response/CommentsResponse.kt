package com.musicplayer.lytrix.innertube.models.response

import com.musicplayer.lytrix.innertube.models.Runs
import com.musicplayer.lytrix.innertube.models.Thumbnails
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
data class CommentsResponse(
    val onResponseReceivedEndpoints: List<ResponseEndpoint>? = null,
    val frameworkUpdates: FrameworkUpdates? = null,
) {
    @Serializable
    data class ResponseEndpoint(
        val reloadContinuationItemsCommand: ContinuationItems? = null,
        val appendContinuationItemsAction: ContinuationItems? = null,
    )

    @Serializable
    data class ContinuationItems(
        val continuationItems: List<Item>? = null,
    )

    @Serializable
    data class Item(
        val commentsHeaderRenderer: HeaderRenderer? = null,
        val commentThreadRenderer: ThreadRenderer? = null,
        val commentViewModel: CommentViewModel? = null,
        val continuationItemRenderer: ContinuationItemRenderer? = null,
    )

    @Serializable
    data class HeaderRenderer(
        val countText: Runs? = null,
        val commentsCount: Runs? = null,
    )

    @Serializable
    data class ThreadRenderer(
        val commentViewModel: CommentViewModelHolder? = null,
        val replies: Replies? = null,
    )

    @Serializable
    data class CommentViewModelHolder(
        val commentViewModel: CommentViewModel? = null,
    )

    @Serializable
    data class CommentViewModel(
        val commentId: String? = null,
        val commentKey: String? = null,
        val toolbarStateKey: String? = null,
        val pinnedText: String? = null,
    )

    @Serializable
    data class Replies(
        val commentRepliesRenderer: RepliesRenderer? = null,
    )

    @Serializable
    data class RepliesRenderer(
        val viewReplies: JsonElement? = null,
        val subThreads: List<Item>? = null,
        val contents: List<Item>? = null,
    )

    @Serializable
    data class ContinuationItemRenderer(
        val continuationEndpoint: JsonElement? = null,
        val button: JsonElement? = null,
    )

    @Serializable
    data class FrameworkUpdates(
        val entityBatchUpdate: EntityBatchUpdate? = null,
    )

    @Serializable
    data class EntityBatchUpdate(
        val mutations: List<Mutation>? = null,
    )

    @Serializable
    data class Mutation(
        val entityKey: String? = null,
        val payload: Payload? = null,
    )

    @Serializable
    data class Payload(
        val commentEntityPayload: CommentEntity? = null,
        val engagementToolbarStateEntityPayload: ToolbarState? = null,
    )

    @Serializable
    data class CommentEntity(
        val properties: Properties? = null,
        val author: Author? = null,
        val toolbar: Toolbar? = null,
    ) {
        @Serializable
        data class Properties(
            val commentId: String? = null,
            val content: Content? = null,
            val publishedTime: String? = null,
        )

        @Serializable
        data class Content(
            val content: String? = null,
        )

        @Serializable
        data class Author(
            val channelId: String? = null,
            val displayName: String? = null,
            val avatarThumbnailUrl: String? = null,
            val isVerified: Boolean? = null,
            val isCreator: Boolean? = null,
        )

        @Serializable
        data class Toolbar(
            val likeCountNotliked: String? = null,
        )
    }

    @Serializable
    data class ToolbarState(
        val heartState: String? = null,
    )
}

data class YouTubeCommentItem(
    val id: String,
    val author: String,
    val authorAvatar: String?,
    val isVerified: Boolean,
    val isCreator: Boolean,
    val text: String,
    val publishedTime: String,
    val likeCount: String,
    val isHearted: Boolean,
    val isPinned: Boolean,
    val repliesLabel: String?,
    val repliesToken: String?,
)

data class YouTubeCommentsPage(
    val countText: String?,
    val countShort: String?,
    val comments: List<YouTubeCommentItem>,
    val nextToken: String?,
    val nextLabel: String?,
    val useWebClient: Boolean = false,
)

object CommentsPageParser {
    fun commentsTabToken(next: NextResponse): String? =
        next.contents.singleColumnMusicWatchNextResultsRenderer
            ?.tabbedRenderer
            ?.watchNextTabbedResultsRenderer
            ?.tabs
            ?.firstNotNullOfOrNull { tab ->
                tab.tabRenderer
                    ?.content
                    ?.sectionListRenderer
                    ?.continuations
                    ?.firstNotNullOfOrNull { it.reloadContinuationData?.continuation }
            }

    fun webCommentsTabToken(next: NextResponse): String? =
        next.contents.twoColumnWatchNextResults
            ?.results
            ?.results
            ?.content
            ?.asSequence()
            ?.mapNotNull { it?.itemSectionRenderer }
            ?.flatMap { it.contents.orEmpty().asSequence() }
            ?.mapNotNull { it?.continuationItemRenderer?.continuationEndpoint?.continuationCommand?.token }
            ?.firstOrNull()

    fun parse(response: CommentsResponse, useWebClient: Boolean = false): YouTubeCommentsPage {
        val entities = response.frameworkUpdates
            ?.entityBatchUpdate
            ?.mutations
            .orEmpty()
            .mapNotNull { m -> m.entityKey?.let { key -> m.payload?.let { key to it } } }
            .toMap()

        var countText: String? = null
        var countShort: String? = null
        var nextToken: String? = null
        var nextLabel: String? = null
        val comments = mutableListOf<YouTubeCommentItem>()

        response.onResponseReceivedEndpoints
            .orEmpty()
            .flatMap { (it.reloadContinuationItemsCommand ?: it.appendContinuationItemsAction)?.continuationItems.orEmpty() }
            .forEach { item ->
                item.commentsHeaderRenderer?.let { header ->
                    countText = header.countText?.runs?.joinToString("") { it.text }?.takeIf { it.isNotBlank() }
                    countShort = header.commentsCount?.runs?.joinToString("") { it.text }?.takeIf { it.isNotBlank() }
                }
                item.commentThreadRenderer?.let { thread ->
                    val replies = thread.replies?.commentRepliesRenderer
                    thread.commentViewModel?.commentViewModel?.toComment(
                        entities = entities,
                        repliesLabel = replies?.viewReplies.findObject("text").runsText(),
                        repliesToken = (replies?.subThreads.orEmpty() + replies?.contents.orEmpty())
                            .firstNotNullOfOrNull { it.continuationItemRenderer?.token() },
                    )?.let(comments::add)
                }
                item.commentViewModel?.toComment(entities, null, null)?.let(comments::add)
                item.continuationItemRenderer?.let {
                    nextToken = it.token()
                    nextLabel = it.button.findObject("text").runsText()
                }
            }

        return YouTubeCommentsPage(
            countText = countText,
            countShort = countShort,
            comments = comments,
            nextToken = nextToken,
            nextLabel = nextLabel,
            useWebClient = useWebClient,
        )
    }

    private fun CommentsResponse.CommentViewModel.toComment(
        entities: Map<String, CommentsResponse.Payload>,
        repliesLabel: String?,
        repliesToken: String?,
    ): YouTubeCommentItem? {
        val entity = commentKey?.let { entities[it]?.commentEntityPayload } ?: return null
        val id = commentId ?: entity.properties?.commentId ?: return null
        val state = toolbarStateKey?.let { entities[it]?.engagementToolbarStateEntityPayload }
        return YouTubeCommentItem(
            id = id,
            author = entity.author?.displayName.orEmpty(),
            authorAvatar = entity.author?.avatarThumbnailUrl,
            isVerified = entity.author?.isVerified == true,
            isCreator = entity.author?.isCreator == true,
            text = entity.properties?.content?.content.orEmpty(),
            publishedTime = entity.properties?.publishedTime.orEmpty(),
            likeCount = entity.toolbar?.likeCountNotliked.orEmpty(),
            isHearted = state?.heartState == "TOOLBAR_HEART_STATE_HEARTED",
            isPinned = pinnedText != null,
            repliesLabel = repliesLabel,
            repliesToken = repliesToken,
        )
    }

    private fun CommentsResponse.ContinuationItemRenderer.token(): String? =
        continuationEndpoint.findString("token") ?: button.findString("token")

    private fun JsonObject?.runsText(): String? {
        if (this == null) return null
        (this["simpleText"] as? JsonPrimitive)?.contentOrNull?.let { return it }
        return (this["runs"] as? JsonArray)
            ?.joinToString("") { ((it as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull.orEmpty() }
            ?.takeIf { it.isNotBlank() }
    }

    private fun JsonElement?.findString(key: String): String? {
        when (this) {
            is JsonObject -> {
                (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.let { return it.content }
                values.forEach { v -> v.findString(key)?.let { return it } }
            }
            is JsonArray -> forEach { v -> v.findString(key)?.let { return it } }
            else -> Unit
        }
        return null
    }

    private fun JsonElement?.findObject(key: String): JsonObject? {
        when (this) {
            is JsonObject -> {
                (this[key] as? JsonObject)?.let { return it }
                values.forEach { v -> v.findObject(key)?.let { return it } }
            }
            is JsonArray -> forEach { v -> v.findObject(key)?.let { return it } }
            else -> Unit
        }
        return null
    }
}
