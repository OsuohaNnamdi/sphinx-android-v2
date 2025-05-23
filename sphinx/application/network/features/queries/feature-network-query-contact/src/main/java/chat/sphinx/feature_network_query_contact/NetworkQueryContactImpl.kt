package chat.sphinx.feature_network_query_contact

import chat.sphinx.concept_network_query_contact.NetworkQueryContact
import chat.sphinx.concept_network_query_contact.model.*
import chat.sphinx.concept_network_relay_call.NetworkRelayCall
import chat.sphinx.feature_network_query_contact.model.*
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.ResponseError
import chat.sphinx.wrapper_common.lightning.LightningNodePubKey
import chat.sphinx.wrapper_relay.RelayUrl
import kotlinx.coroutines.flow.Flow

class NetworkQueryContactImpl(
    private val networkRelayCall: NetworkRelayCall,
): NetworkQueryContact() {

    companion object {
        private const val ENDPOINT_HAS_ADMIN = "/has_admin"
        private const val ENDPOINT_PRODUCTION_CONFIG = "https://config.config.sphinx.chat/api/config/bitcoin"
        private const val ENDPOINT_TEST_CONFIG = "https://config.config.sphinx.chat/api/config/regtest"
        private const val ENDPOINT_ROUTE = "/api/route?pubkey=%s&msat=%s"

        private const val ENDPOINT_GET_NODES = "/api/node"
        private const val PROTOCOL_HTTPS = "https://"
    }

    ///////////
    /// GET ///
    ///////////

    override fun hasAdmin(
        url: RelayUrl
    ): Flow<LoadResponse<Any, ResponseError>> =
        networkRelayCall.get(
            url = "${url.value}$ENDPOINT_HAS_ADMIN",
            responseJsonClass = HasAdminRelayResponse::class.java,
        )

    override fun getAccountConfig(isProductionEnvironment: Boolean): Flow<LoadResponse<AccountConfigV2Response, ResponseError>> =
        networkRelayCall.get(
            if (isProductionEnvironment) ENDPOINT_PRODUCTION_CONFIG else ENDPOINT_TEST_CONFIG,
            responseJsonClass = AccountConfigV2Response::class.java
        )

    override fun getNodes(routerUrl: String): Flow<LoadResponse<String, ResponseError>> =
        networkRelayCall.getRawJson(
            url = PROTOCOL_HTTPS +  routerUrl + ENDPOINT_GET_NODES
        )

    override fun getRoutingNodes(
        routerUrl: String,
        lightningNodePubKey: LightningNodePubKey,
        milliSats: Long
    ): Flow<LoadResponse<String, ResponseError>> {
        val url = PROTOCOL_HTTPS + routerUrl + ENDPOINT_ROUTE.format(lightningNodePubKey.value, milliSats)

        return networkRelayCall.getRawJson(
            url = url
        )
    }

}
