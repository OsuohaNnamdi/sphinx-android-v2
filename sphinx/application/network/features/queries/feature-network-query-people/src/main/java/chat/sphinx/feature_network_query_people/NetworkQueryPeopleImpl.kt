package chat.sphinx.feature_network_query_people

import chat.sphinx.concept_network_query_people.NetworkQueryPeople
import chat.sphinx.concept_network_query_people.model.*
import chat.sphinx.concept_network_relay_call.NetworkRelayCall
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.ResponseError
import chat.sphinx.wrapper_common.chat.ChatUUID
import chat.sphinx.wrapper_message.MessagePerson
import chat.sphinx.wrapper_message.host
import chat.sphinx.wrapper_message.uuid
import kotlinx.coroutines.flow.Flow

class NetworkQueryPeopleImpl(
    private val networkRelayCall: NetworkRelayCall,
): NetworkQueryPeople() {

    companion object {
        private const val TRIBES_DEFAULT_SERVER_URL = "https://people.sphinx.chat"
        private const val LIQUID_DEFAULT_SERVER_URL = "https://liquid.sphinx.chat"

        private const val ENDPOINT_SAVE_KEY = "https://%s/save/%s"
        private const val ENDPOINT_PROFILE = "/profile"
        private const val ENDPOINT_TRIBE_MEMBER_PROFILE = "https://%s/person/uuid/%s"
        private const val ENDPOINT_TRIBE_LEADERBOARD = "$TRIBES_DEFAULT_SERVER_URL/leaderboard/%s"
        private const val ENDPOINT_TRIBE_BADGES = "https://%s/person/uuid/%s/assets"
        private const val ENDPOINT_TRIBE_BADGES_TEMPLATES = "/badge_templates"
        private const val ENDPOINT_TRIBE_EXISTING_BADGES = "/badge_per_tribe/%s?limit=100&offset=0"
        private const val ENDPOINT_TRIBE_ACTIVE_BADGE = "/add_badge"
        private const val ENDPOINT_TRIBE_DEACTIVATE_BADGE = "/remove_badge"
        private const val ENDPOINT_TRIBE_CREATE_BADGE = "/create_badge"
        private const val ENDPOINT_KNOWN_BADGES = "$LIQUID_DEFAULT_SERVER_URL/asset/filter"
        private const val ENDPOINT_CALL_TOKEN = "https://chat.sphinx.chat/api/connection-details?roomName=%s&participantName=%s"
    }

    override fun getTribeMemberProfile(
        person: MessagePerson
    ): Flow<LoadResponse<TribeMemberProfileDto, ResponseError>> =
        networkRelayCall.get(
            url = String.format(
                ENDPOINT_TRIBE_MEMBER_PROFILE,
                person.host(),
                person.uuid()
            ),
            responseJsonClass = TribeMemberProfileDto::class.java,
        )

    override fun getExternalRequestByKey(
        host: String,
        key: String
    ): Flow<LoadResponse<GetExternalRequestDto, ResponseError>> =
        networkRelayCall.get(
            url = String.format(
                ENDPOINT_SAVE_KEY,
                host,
                key
            ),
            responseJsonClass = GetExternalRequestDto::class.java,
        )

    override fun getLeaderboard(
        tribeUUID: ChatUUID
    ): Flow<LoadResponse<List<ChatLeaderboardDto>, ResponseError>> =
        networkRelayCall.getList(
            url = String.format(ENDPOINT_TRIBE_LEADERBOARD, tribeUUID.value),
            responseJsonClass = ChatLeaderboardDto::class.java,
        )

    override fun getKnownBadges(
        badgeIds: Array<Long>
    ): Flow<LoadResponse<List<BadgeDto>, ResponseError>> =
        networkRelayCall.postList(
            url = ENDPOINT_KNOWN_BADGES,
            responseJsonClass = BadgeDto::class.java,
            requestBodyJsonClass = KnownBadgesPostDto::class.java,
            requestBody = KnownBadgesPostDto(badgeIds.toList()),
        )

    override fun getBadgesByPerson(
        person: MessagePerson
    ): Flow<LoadResponse<List<BadgeDto>, ResponseError>> =
        networkRelayCall.getList(
            url = String.format(ENDPOINT_TRIBE_BADGES, person.host(), person.uuid()),
            responseJsonClass = BadgeDto::class.java,
        )

    override fun getLiveKitToken(
        room: String,
        alias: String,
        profilePictureUrl: String?
    ): Flow<LoadResponse<CallTokenDto, ResponseError>> {
        profilePictureUrl?.let {
            return networkRelayCall.get(
                url = String.format(ENDPOINT_CALL_TOKEN, room, alias) + "&metadata={\"profilePictureUrl\":\"${it}\"}",
                responseJsonClass = CallTokenDto::class.java,
            )
        }
        return networkRelayCall.get(
            url = String.format(ENDPOINT_CALL_TOKEN, room, alias),
            responseJsonClass = CallTokenDto::class.java,
        )

    }

}
