package chat.sphinx.feature_repository

import app.cash.exhaustive.Exhaustive
import chat.sphinx.concept_coredb.CoreDB
import chat.sphinx.concept_crypto_rsa.RSA
import chat.sphinx.concept_meme_input_stream.MemeInputStreamHandler
import chat.sphinx.concept_meme_server.MemeServerTokenHandler
import chat.sphinx.concept_network_query_chat.NetworkQueryChat
import chat.sphinx.concept_network_query_chat.model.*
import chat.sphinx.concept_network_query_contact.NetworkQueryContact
import chat.sphinx.concept_network_query_discover_tribes.NetworkQueryDiscoverTribes
import chat.sphinx.concept_network_query_feed_search.NetworkQueryFeedSearch
import chat.sphinx.concept_network_query_feed_search.model.toFeedSearchResult
import chat.sphinx.concept_network_query_feed_status.NetworkQueryFeedStatus
import chat.sphinx.concept_network_query_feed_status.model.ContentFeedStatusDto
import chat.sphinx.concept_network_query_feed_status.model.EpisodeStatusDto
import chat.sphinx.concept_network_query_invite.NetworkQueryInvite
import chat.sphinx.concept_network_query_meme_server.NetworkQueryMemeServer
import chat.sphinx.concept_network_query_people.NetworkQueryPeople
import chat.sphinx.concept_network_query_people.model.DeletePeopleProfileDto
import chat.sphinx.concept_network_query_people.model.PeopleProfileDto
import chat.sphinx.concept_network_query_verify_external.NetworkQueryAuthorizeExternal
import chat.sphinx.concept_network_query_verify_external.model.RedeemSatsDto
import chat.sphinx.concept_relay.RelayDataHandler
import chat.sphinx.concept_repository_actions.ActionsRepository
import chat.sphinx.concept_repository_chat.ChatRepository
import chat.sphinx.concept_repository_chat.model.AddMember
import chat.sphinx.concept_repository_chat.model.CreateTribe
import chat.sphinx.concept_repository_connect_manager.ConnectManagerRepository
import chat.sphinx.concept_repository_connect_manager.model.NetworkStatus
import chat.sphinx.concept_repository_connect_manager.model.OwnerRegistrationState
import chat.sphinx.concept_repository_connect_manager.model.RestoreProcessState
import chat.sphinx.concept_repository_contact.ContactRepository
import chat.sphinx.concept_repository_dashboard.RepositoryDashboard
import chat.sphinx.concept_repository_feed.FeedRepository
import chat.sphinx.concept_repository_lightning.LightningRepository
import chat.sphinx.concept_repository_media.RepositoryMedia
import chat.sphinx.concept_repository_message.MessageRepository
import chat.sphinx.concept_repository_message.model.AttachmentInfo
import chat.sphinx.concept_repository_message.model.SendMessage
import chat.sphinx.concept_repository_message.model.SendPayment
import chat.sphinx.concept_repository_message.model.SendPaymentRequest
import chat.sphinx.concept_repository_subscription.SubscriptionRepository
import chat.sphinx.concept_wallet.WalletDataHandler
import chat.sphinx.conceptcoredb.*
import chat.sphinx.example.concept_connect_manager.ConnectManager
import chat.sphinx.example.concept_connect_manager.ConnectManagerListener
import chat.sphinx.example.concept_connect_manager.model.OwnerInfo
import chat.sphinx.example.wrapper_mqtt.ConnectManagerError
import chat.sphinx.example.wrapper_mqtt.LastReadMessages.Companion.toLastReadMap
import chat.sphinx.example.wrapper_mqtt.MessageDto
import chat.sphinx.example.wrapper_mqtt.MessageMetadata
import chat.sphinx.example.wrapper_mqtt.MessageMetadata.Companion.toMessageMetadata
import chat.sphinx.example.wrapper_mqtt.MsgsCounts
import chat.sphinx.example.wrapper_mqtt.MsgsCounts.Companion.toMsgsCounts
import chat.sphinx.example.wrapper_mqtt.MuteLevels.Companion.toMuteLevelsMap
import chat.sphinx.example.wrapper_mqtt.NewCreateTribe.Companion.toNewCreateTribe
import chat.sphinx.example.wrapper_mqtt.NewSentStatus.Companion.toNewSentStatus
import chat.sphinx.example.wrapper_mqtt.Payment.Companion.toPaymentsList
import chat.sphinx.example.wrapper_mqtt.TagMessageList.Companion.toTagsList
import chat.sphinx.example.wrapper_mqtt.TransactionDto
import chat.sphinx.example.wrapper_mqtt.TribeMembersResponse
import chat.sphinx.example.wrapper_mqtt.TribeMembersResponse.Companion.toTribeMembersList
import chat.sphinx.example.wrapper_mqtt.toLspChannelInfo
import chat.sphinx.feature_repository.mappers.action_track.*
import chat.sphinx.feature_repository.mappers.chat.ChatDboPresenterMapper
import chat.sphinx.feature_repository.mappers.contact.ContactDboPresenterMapper
import chat.sphinx.feature_repository.mappers.feed.*
import chat.sphinx.feature_repository.mappers.feed.podcast.FeedDboFeedSearchResultPresenterMapper
import chat.sphinx.feature_repository.mappers.feed.podcast.FeedDboPodcastPresenterMapper
import chat.sphinx.feature_repository.mappers.feed.podcast.FeedItemDboPodcastEpisodePresenterMapper
import chat.sphinx.feature_repository.mappers.feed.podcast.FeedRecommendationPodcastPresenterMapper
import chat.sphinx.feature_repository.mappers.invite.InviteDboPresenterMapper
import chat.sphinx.feature_repository.mappers.lsat.LsatDboPresenterMapper
import chat.sphinx.feature_repository.mappers.mapListFrom
import chat.sphinx.feature_repository.mappers.message.MessageDboPresenterMapper
import chat.sphinx.feature_repository.mappers.subscription.SubscriptionDboPresenterMapper
import chat.sphinx.feature_repository.model.message.MessageDboWrapper
import chat.sphinx.feature_repository.model.message.MessageMediaDboWrapper
import chat.sphinx.feature_repository.model.message.convertMessageDboToNewMessage
import chat.sphinx.feature_repository.util.*
import chat.sphinx.kotlin_response.*
import chat.sphinx.logger.SphinxLogger
import chat.sphinx.logger.d
import chat.sphinx.logger.e
import chat.sphinx.logger.w
import chat.sphinx.notification.SphinxNotificationManager
import chat.sphinx.wrapper_action_track.ActionTrackId
import chat.sphinx.wrapper_action_track.ActionTrackMetaData
import chat.sphinx.wrapper_action_track.ActionTrackType
import chat.sphinx.wrapper_action_track.action_wrappers.*
import chat.sphinx.wrapper_action_track.toActionTrackUploaded
import chat.sphinx.wrapper_chat.*
import chat.sphinx.wrapper_common.*
import chat.sphinx.wrapper_common.chat.ChatUUID
import chat.sphinx.wrapper_common.chat.toChatUUID
import chat.sphinx.wrapper_common.contact.Blocked
import chat.sphinx.wrapper_common.contact.isTrue
import chat.sphinx.wrapper_common.dashboard.*
import chat.sphinx.wrapper_common.feed.*
import chat.sphinx.wrapper_common.invite.InviteStatus
import chat.sphinx.wrapper_common.lightning.Bolt11
import chat.sphinx.wrapper_common.lightning.LightningNodePubKey
import chat.sphinx.wrapper_common.lightning.LightningPaymentHash
import chat.sphinx.wrapper_common.lightning.LightningPaymentRequest
import chat.sphinx.wrapper_common.lightning.LightningRouteHint
import chat.sphinx.wrapper_common.lightning.MilliSat
import chat.sphinx.wrapper_common.lightning.Sat
import chat.sphinx.wrapper_common.lightning.ServerIp
import chat.sphinx.wrapper_common.lightning.getLspPubKey
import chat.sphinx.wrapper_common.lightning.getScid
import chat.sphinx.wrapper_common.lightning.milliSatsToSats
import chat.sphinx.wrapper_common.lightning.toLightningNodePubKey
import chat.sphinx.wrapper_common.lightning.toLightningPaymentHash
import chat.sphinx.wrapper_common.lightning.toLightningPaymentRequestOrNull
import chat.sphinx.wrapper_common.lightning.toLightningRouteHint
import chat.sphinx.wrapper_common.lightning.toMilliSat
import chat.sphinx.wrapper_common.lightning.toSat
import chat.sphinx.wrapper_common.lsat.Lsat
import chat.sphinx.wrapper_common.lsat.LsatIdentifier
import chat.sphinx.wrapper_common.lsat.LsatIssuer
import chat.sphinx.wrapper_common.lsat.LsatStatus
import chat.sphinx.wrapper_common.message.*
import chat.sphinx.wrapper_common.payment.PaymentTemplate
import chat.sphinx.wrapper_contact.*
import chat.sphinx.wrapper_feed.*
import chat.sphinx.wrapper_invite.Invite
import chat.sphinx.wrapper_invite.InviteCode
import chat.sphinx.wrapper_invite.InviteString
import chat.sphinx.wrapper_io_utils.InputStreamProvider
import chat.sphinx.wrapper_lightning.LightningServiceProvider
import chat.sphinx.wrapper_lightning.NodeBalance
import chat.sphinx.wrapper_lightning.toWalletMnemonic
import chat.sphinx.wrapper_message.*
import chat.sphinx.wrapper_message.Msg.Companion.toMsg
import chat.sphinx.wrapper_message.MsgSender.Companion.toMsgSender
import chat.sphinx.wrapper_message.MsgSender.Companion.toMsgSenderNull
import chat.sphinx.wrapper_message_media.*
import chat.sphinx.wrapper_message_media.token.MediaHost
import chat.sphinx.wrapper_podcast.ChapterResponseDto
import chat.sphinx.wrapper_podcast.FeedRecommendation
import chat.sphinx.wrapper_podcast.FeedSearchResultRow
import chat.sphinx.wrapper_podcast.Podcast
import chat.sphinx.wrapper_podcast.PodcastEpisode
import chat.sphinx.wrapper_rsa.RsaPrivateKey
import chat.sphinx.wrapper_rsa.RsaPublicKey
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import io.matthewnelson.concept_authentication.data.AuthenticationStorage
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import io.matthewnelson.concept_media_cache.MediaCacheHandler
import io.matthewnelson.crypto_common.annotations.RawPasswordAccess
import io.matthewnelson.crypto_common.annotations.UnencryptedDataAccess
import io.matthewnelson.crypto_common.clazzes.*
import io.matthewnelson.feature_authentication_core.AuthenticationCoreManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.base64.encodeBase64
import java.io.File
import java.io.InputStream
import java.security.SecureRandom
import java.util.*


abstract class SphinxRepository(
    override val accountOwner: StateFlow<Contact?>,
    private val applicationScope: CoroutineScope,
    private val authenticationCoreManager: AuthenticationCoreManager,
    private val authenticationStorage: AuthenticationStorage,
    private val relayDataHandler: RelayDataHandler,
    protected val coreDB: CoreDB,
    private val dispatchers: CoroutineDispatchers,
    private val moshi: Moshi,
    private val mediaCacheHandler: MediaCacheHandler,
    private val memeInputStreamHandler: MemeInputStreamHandler,
    private val memeServerTokenHandler: MemeServerTokenHandler,
    private val networkQueryDiscoverTribes: NetworkQueryDiscoverTribes,
    private val networkQueryMemeServer: NetworkQueryMemeServer,
    private val networkQueryChat: NetworkQueryChat,
    private val networkQueryContact: NetworkQueryContact,
    private val networkQueryInvite: NetworkQueryInvite,
    private val networkQueryAuthorizeExternal: NetworkQueryAuthorizeExternal,
    private val networkQueryPeople: NetworkQueryPeople,
    private val networkQueryFeedSearch: NetworkQueryFeedSearch,
    private val networkQueryFeedStatus: NetworkQueryFeedStatus,
    private val connectManager: ConnectManager,
    private val walletDataHandler: WalletDataHandler,
    private val rsa: RSA,
    private val sphinxNotificationManager: SphinxNotificationManager,
    private val LOG: SphinxLogger,
) : ChatRepository,
    ContactRepository,
    LightningRepository,
    MessageRepository,
    SubscriptionRepository,
    RepositoryDashboard,
    RepositoryMedia,
    ActionsRepository,
    FeedRepository,
    ConnectManagerRepository,
    CoroutineDispatchers by dispatchers,
    ConnectManagerListener
{

    companion object {
        const val TAG: String = "SphinxRepository"

        // PersistentStorage Keys
        const val REPOSITORY_LIGHTNING_BALANCE = "REPOSITORY_LIGHTNING_BALANCE"
        const val REPOSITORY_LAST_SEEN_MESSAGE_DATE = "REPOSITORY_LAST_SEEN_MESSAGE_DATE"
        const val REPOSITORY_LAST_SEEN_MESSAGE_RESTORE_PAGE = "REPOSITORY_LAST_SEEN_MESSAGE_RESTORE_PAGE"
        const val REPOSITORY_PUSH_KEY = "REPOSITORY_PUSH_KEY"

        const val MEDIA_KEY_SIZE = 32
    }

    ////////////////////////
    /// Connect Manager ///
    //////////////////////

    override val connectionManagerState: MutableStateFlow<OwnerRegistrationState?> by lazy {
        MutableStateFlow(null)
    }

    override val networkStatus: MutableStateFlow<NetworkStatus> by lazy {
        MutableStateFlow(NetworkStatus.Loading)
    }

    override val restoreProcessState: MutableStateFlow<RestoreProcessState?> by lazy {
        MutableStateFlow(null)
    }

    override val connectManagerErrorState: MutableStateFlow<ConnectManagerError?> by lazy {
        MutableStateFlow(null)
    }

    override val transactionDtoState: MutableStateFlow<List<TransactionDto>?> by lazy {
        MutableStateFlow(null)
    }

    override val userStateFlow: MutableStateFlow<String?> by lazy {
        MutableStateFlow(null)
    }

    override val tribeMembersState: MutableStateFlow<TribeMembersResponse?> by lazy {
        MutableStateFlow(null)
    }

    override val restoreProgress: MutableStateFlow<Int?> by lazy {
        MutableStateFlow(null)
    }

    override val webViewPaymentHash: MutableStateFlow<String?> by lazy {
        MutableStateFlow(null)
    }

    override val processingInvoice: MutableStateFlow<Pair<String, String>?> by lazy {
        MutableStateFlow(null)
    }

    override val webViewPreImage: MutableStateFlow<String?> by lazy {
        MutableStateFlow(null)
    }

    override val restoreMinIndex: MutableStateFlow<Long?> by lazy {
        MutableStateFlow(null)
    }

    override val createProjectTimestamps: MutableStateFlow<MutableMap<String, Long>> by lazy {
        MutableStateFlow(mutableMapOf())
    }

    init {
        connectManager.addListener(this)
        memeServerTokenHandler.addListener(this)
    }

    override fun connectAndSubscribeToMqtt(userState: String?, mixerIp: String?) {
        applicationScope.launch(mainImmediate) {
            val queries = coreDB.getSphinxDatabaseQueries()
            val mnemonic = walletDataHandler.retrieveWalletMnemonic()
            var owner: Contact? = accountOwner.value

            if (owner == null) {
                try {
                    accountOwner.collect { contact ->
                        if (contact != null) {
                            owner = contact
                            throw Exception()
                        }
                    }
                } catch (e: Exception) {
                }
                delay(25L)
            }

            val okKey = owner?.nodePubKey?.value
            val lastMessageIndex = queries.messageGetMaxId().executeAsOneOrNull()?.MAX

            val ownerInfo = OwnerInfo(
                owner?.alias?.value ?: "",
                owner?.photoUrl?.value ?: "",
                owner?.nodePubKey?.value,
                owner?.routeHint?.value,
                userState,
                lastMessageIndex
            )

            if (mnemonic != null && okKey != null && mixerIp != null) {
                connectManager.initializeMqttAndSubscribe(
                    mixerIp,
                    mnemonic,
                    ownerInfo
                )
            } else {
                val logMixerIp = !mixerIp.isNullOrEmpty()
                val logMnemonic = !mnemonic?.value.isNullOrEmpty()
                val logOkKey = !okKey.isNullOrEmpty()

                connectManagerErrorState.value = ConnectManagerError.MqttInitError(
                    "mixerIp: $logMixerIp mnemonic: $logMnemonic okKey: $logOkKey"
                )
            }
        }
    }

    override fun createOwnerAccount() {
        applicationScope.launch(mainImmediate) {
            val mnemonic = walletDataHandler.retrieveWalletMnemonic()
            connectManager.createAccount(mnemonic?.value)
        }
    }

    override fun resetAccount() {
        setInviteCode(null)
        setMnemonicWords(emptyList())
        connectionManagerState.value = null
        connectManagerErrorState.value = null

        applicationScope.launch(io) {
            clearDatabase()
        }
    }


    override fun startRestoreProcess() {
        applicationScope.launch(mainImmediate) {
            var msgCounts: MsgsCounts? = null

            restoreProcessState.asStateFlow().collect { restoreProcessState ->
                when (restoreProcessState) {
                    is RestoreProcessState.MessagesCounts -> {
                        msgCounts = restoreProcessState.msgsCounts
                        connectManager.fetchFirstMessagesPerKey(0L, msgCounts?.first_for_each_scid)
                    }

                    is RestoreProcessState.RestoreMessages -> {
                        delay(100L)
                        connectManager.fetchMessagesOnRestoreAccount(
                            msgCounts?.total_highest_index,
                            msgCounts?.total
                        )
                    }
                    else -> {}
                }
            }
        }
    }

    override fun createContact(contact: NewContact) {
        applicationScope.launch(mainImmediate) {
            createNewContact(contact)
            connectManager.createContact(contact)
        }
    }

    override fun setInviteCode(inviteString: String?) {
        connectManager.setInviteCode(inviteString)
    }

    override fun setMnemonicWords(words: List<String>?) {
        connectManager.setMnemonicWords(words)
    }

    override fun setNetworkType(isTestEnvironment: Boolean) {
        connectManager.setNetworkType(isTestEnvironment)
    }

    override fun setOwnerDeviceId(deviceId: String) {
        applicationScope.launch(mainImmediate) {
            var pushKey: String? = authenticationStorage.getString(
                REPOSITORY_PUSH_KEY,
                null
            )

            if (pushKey == null) {
                val newPushKey = generateRandomBytes(32).toHex()
                LOG.d("PUSH_KEY", newPushKey)
                authenticationStorage.putString(REPOSITORY_PUSH_KEY, newPushKey)
            }

            pushKey?.let {
                connectManager.setOwnerDeviceId(deviceId, it)
            }
        }
    }

    override fun signChallenge(challenge: String): String? {
        return connectManager.processChallengeSignature(challenge)
    }

    override fun createInvite(
        nickname: String,
        welcomeMessage: String,
        sats: Long,
        serverDefaultTribe: String?,
        tribeServerIp: String?,
        mixerIp: String?
    ) {
        applicationScope.launch(io) {
            connectManager.createInvite(
                nickname,
                welcomeMessage,
                sats,
                serverDefaultTribe,
                tribeServerIp,
                mixerIp
            )
        }
    }

    override fun joinTribe(
        tribeHost: String,
        tribePubKey: String,
        tribeRouteHint: String,
        tribeName: String,
        tribePicture: String?,
        isPrivate: Boolean,
        userAlias: String,
        pricePerMessage: Long,
        escrowAmount: Long,
        priceToJoin: Long
    ) {
        connectManager.joinToTribe(
            tribeHost,
            tribePubKey,
            tribeRouteHint,
            isPrivate,
            userAlias,
            priceToJoin
        )

        applicationScope.launch(io) {
            val queries = coreDB.getSphinxDatabaseQueries()

            // TribeId is set from LONG.MAX_VALUE and decremented by 1 for each new tribe
            val tribeId =
                queries.chatGetLastTribeId().executeAsOneOrNull()?.let { it.MIN?.minus(1) }
                    ?: (Long.MAX_VALUE)
            val now: String = DateTime.nowUTC()

            val newTribe = Chat(
                id = ChatId(tribeId),
                uuid = ChatUUID(tribePubKey),
                name = ChatName(tribeName ?: "unknown"),
                photoUrl = tribePicture?.toPhotoUrl(),
                type = ChatType.Tribe,
                status = ChatStatus.Approved,
                contactIds = listOf(ContactId(0), ContactId(tribeId)),
                isMuted = ChatMuted.False,
                createdAt = now.toDateTime(),
                groupKey = null,
                host = ChatHost(tribeHost),
                pricePerMessage = pricePerMessage.toSat(),
                escrowAmount = escrowAmount.toSat(),
                unlisted = ChatUnlisted.False,
                privateTribe = ChatPrivate.False,
                ownerPubKey = LightningNodePubKey(tribePubKey),
                seen = Seen.False,
                metaData = null,
                myPhotoUrl = null,
                myAlias = userAlias.toChatAlias(),
                pendingContactIds = emptyList(),
                latestMessageId = null,
                contentSeenAt = null,
                pinedMessage = null,
                notify = NotificationLevel.SeeAll,
                secondBrainUrl = null,
                timezoneEnabled = null,
                timezoneIdentifier = null,
                remoteTimezoneIdentifier = null,
                timezoneUpdated = null
            )

            chatLock.withLock {
                queries.transaction {
                    upsertNewChat(
                        newTribe,
                        moshi,
                        SynchronizedMap<ChatId, Seen>(),
                        queries,
                        null,
                        accountOwner.value?.nodePubKey
                    )
                }
            }
        }
    }

    override fun getTribeMembers(tribeServerPubKey: String, tribePubKey: String) {
        connectManager.retrieveTribeMembersList(tribeServerPubKey, tribePubKey)
    }

    override fun getTribeServerPubKey(): String? {
        return connectManager.getTribeServerPubKey()
    }

    override fun getPayments(lastMessageDate: Long, limit: Int) {
        connectManager.getPayments(
            lastMessageDate,
            limit,
            null,
            null,
            null,
            true
        )
    }

    override suspend fun getChatIdByEncryptedChild(child: String) = flow {
        val queries = coreDB.getSphinxDatabaseQueries()

        val pubkey = connectManager.getPubKeyByEncryptedChild(
            child,
            authenticationStorage.getString(REPOSITORY_PUSH_KEY, null)
        )
        if (pubkey != null) {
            val contact = withContext(io) {
                queries.contactGetByPubKey(pubkey.toLightningNodePubKey()).executeAsOneOrNull()
            }
            val tribe = withContext(io) {
                queries.chatGetByUUID(ChatUUID(pubkey)).executeAsOneOrNull()
            }
            emit(contact?.id?.value?.toChatId() ?: tribe?.id)
        } else {
            emit(null)
        }
    }.flowOn(mainImmediate)

    override fun getTagsByChatId(chatId: ChatId) {
        applicationScope.launch(io) {
            getSentConfirmedMessagesByChatId(chatId).collect { messages ->
            if (messages.isNotEmpty()) {
                val tags = messages.mapNotNull { it.tagMessage?.value }.distinct()
                connectManager.getMessagesStatusByTags(tags)
            }
        }
        }
    }

    override suspend fun updateLspAndOwner(data: String) {
        val lspChannelInfo = data.toLspChannelInfo(moshi)
        val serverIp = connectManager.retrieveLspIp()
        val serverPubKey = lspChannelInfo?.serverPubKey

        if (serverIp?.isNotEmpty() == true && serverPubKey != null) {
            updateLSP(
                LightningServiceProvider(
                    ServerIp(serverIp),
                    serverPubKey
                )
            )
        }
    }

    override fun requestNodes(nodeUrl: String) {
        applicationScope.launch(mainImmediate) {
            networkQueryContact.getNodes(nodeUrl).collect { loadResponse ->
                when (loadResponse) {
                    is Response.Success -> {
                        val nodes = loadResponse.value
                        connectionManagerState.value = OwnerRegistrationState.StoreRouterPubKey(nodes)
                        connectManager.addNodesFromResponse(nodes)
                    }
                    else -> {}
                }
            }
        }
    }

    override fun getInvoiceInfo(invoice: String): String? {
        return connectManager.getInvoiceInfo(invoice)
    }

    override fun getSignedTimeStamps(): String? {
        return connectManager.getSignedTimeStamps()
    }

    override fun getSignBase64(text: String): String? {
        return connectManager.getSignBase64(text)
    }

    override fun getIdFromMacaroon(macaroon: String): String? {
        return connectManager.getIdFromMacaroon(macaroon)
    }

    override fun attemptReconnectOnResume() {
        connectManager.attemptReconnectOnResume()
    }

    override suspend fun exitAndDeleteTribe(tribe: Chat) {
        val queries = coreDB.getSphinxDatabaseQueries()
        applicationScope.launch(io) {

            val currentProvisionalId: MessageId? = withContext(io) {
                queries.messageGetLowestProvisionalMessageId().executeAsOneOrNull()
            }
            val provisionalId = MessageId((currentProvisionalId?.value ?: 0L) - 1)
            val isDeleteTribe = tribe.isTribeOwnedByAccount(accountOwner.value?.nodePubKey)
            val messageType = if(isDeleteTribe) MessageType.TRIBE_DELETE else MessageType.GROUP_LEAVE

            val newMessage = chat.sphinx.example.wrapper_mqtt.Message(
                "",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
            ).toJson(moshi)

            tribe.uuid.value.let { pubKey ->
                deleteAllMessagesAndPubKey(pubKey, tribe.id)

                connectManager.sendMessage(
                    newMessage,
                    pubKey,
                    provisionalId.value,
                    messageType,
                    null,
                    true
                )
            }

            chatLock.withLock {
                messageLock.withLock {
                    withContext(io) {
                        queries.transaction {
                            deleteChatById(
                                tribe.id,
                                queries,
                                latestMessageUpdatedTimeMap
                            )
                        }
                    }
                }
            }
        }.join()
    }

    override fun saveNewContactRegistered(
        msgSender: String,
        date: Long?
    ) {
        applicationScope.launch(mainImmediate) {
            val contactInfo = msgSender.toMsgSender(moshi)
            val contact = NewContact(
                contactAlias = contactInfo.alias?.toContactAlias(),
                lightningNodePubKey = contactInfo.pubkey.toLightningNodePubKey(),
                lightningRouteHint = contactInfo.route_hint?.toLightningRouteHint(),
                photoUrl = contactInfo.photo_url?.toPhotoUrl(),
                confirmed = contactInfo.confirmed,
                null,
                inviteCode = contactInfo.code,
                invitePrice = null,
                null,
                date?.toDateTime()
            )

            if (contactInfo.code != null) {
                updateNewContactInvited(contact)
            } else {
                createNewContact(contact)
            }
        }
    }

    override suspend fun getOwnerContact(): Contact? {
        var owner: Contact? = null

        applicationScope.launch(mainImmediate) {
            coreDB.getSphinxDatabaseQueries().contactGetOwner().executeAsOneOrNull()?.let {
                owner = contactDboPresenterMapper.mapFrom(it)
            }
        }.join()

        return owner
    }

    override fun updateNewContactInvited(contact: NewContact) {
        applicationScope.launch(io) {
            val queries = coreDB.getSphinxDatabaseQueries()
            val invite = queries.inviteGetByCode(contact.inviteCode?.let { InviteCode(it) }).executeAsOneOrNull()

            if (invite != null) {
                val contactId = invite.contact_id

                queries.contactUpdateInvitee(
                    contact.contactAlias,
                    contact.photoUrl,
                    contact.lightningNodePubKey,
                    ContactStatus.Confirmed,
                    contact.lightningRouteHint,
                    ContactId(invite.id.value)
                )

                queries.inviteUpdateStatus(InviteStatus.Complete, invite.id)
                queries.chatUpdateNameAndStatus(
                    ChatName(contact.contactAlias?.value ?: "unknown"),
                    ChatStatus.Approved,
                    ChatId(contactId.value)
                )
                queries.dashboardUpdateConversation(
                    contact.contactAlias?.value,
                    contact.photoUrl,
                    contactId
                )

            } else {
            }
        }
    }

    // ConnectManagerListener Callbacks implemented

    // Account Management
    override fun onUpdateUserState(userState: String) {
        userStateFlow.value = userState
    }
    override fun onMnemonicWords(
        words: String,
        isRestore: Boolean
    ) {
        applicationScope.launch(io) {
            words.toWalletMnemonic()?.let {
                walletDataHandler.persistWalletMnemonic(it)
            }
        }
    }

    override fun showMnemonic(isRestore: Boolean) {
        if (!isRestore) {
            applicationScope.launch(mainImmediate) {
                walletDataHandler.retrieveWalletMnemonic()?.let { words ->
                    connectionManagerState.value = OwnerRegistrationState.MnemonicWords(words.value)
                }
            }
        }

    }

    override fun onOwnerRegistered(
        okKey: String,
        routeHint: String,
        isRestoreAccount: Boolean,
        mixerServerIp: String?,
        tribeServerHost: String?,
        isProductionEnvironment: Boolean,
        routerUrl: String?,
        defaultTribe: String?
    ) {
        applicationScope.launch(mainImmediate) {
            val scid = routeHint.toLightningRouteHint()?.getScid()

            if (scid != null && accountOwner.value?.nodePubKey == null) {
                createOwner(okKey, routeHint, scid)

                connectionManagerState.value = OwnerRegistrationState.OwnerRegistered(
                    isRestoreAccount,
                    mixerServerIp,
                    tribeServerHost,
                    isProductionEnvironment,
                    routerUrl,
                    defaultTribe
                )
                delay(1000L)

                if (isRestoreAccount) {
                    startRestoreProcess()
                }
            }
        }
    }

    override fun onRestoreAccount(isProductionEnvironment: Boolean) {
        applicationScope.launch(mainImmediate) {
            networkQueryContact.getAccountConfig(isProductionEnvironment).collect { loadResponse ->
                when (loadResponse) {
                    is Response.Success -> {
                        connectManager.restoreAccount(
                            loadResponse.value.tribe,
                            loadResponse.value.tribe_host,
                            loadResponse.value.default_lsp,
                            loadResponse.value.router
                        )
                    }
                    is Response.Error -> {
                        connectManager.restoreFailed()
                    }
                    else -> {}
                }
            }
        }
    }

    override fun onUpsertContacts(
        contacts: List<Pair<String?, Long?>>,
        callback: (() -> Unit)?
    ) {
        if (contacts.isEmpty()) {
            callback?.let { nnCallback ->
                nnCallback()
            }
            return
        }
        applicationScope.launch(mainImmediate) {
            val contactList: List<Pair<MsgSender?, DateTime?>> = contacts.mapNotNull { contact ->
                Pair(contact?.first?.toMsgSenderNull(moshi), contact.second?.toDateTime())
            }.groupBy { it.first?.pubkey }
                .map { (_, group) ->
                    group.find { it.first?.confirmed == true } ?: group.first()
                }

            val newContactList = contactList.map { contactInfo ->
                NewContact(
                    contactAlias = contactInfo.first?.alias?.toContactAlias(),
                    lightningNodePubKey = contactInfo.first?.pubkey?.toLightningNodePubKey(),
                    lightningRouteHint = contactInfo.first?.route_hint?.toLightningRouteHint(),
                    photoUrl = contactInfo.first?.photo_url?.toPhotoUrl(),
                    confirmed = contactInfo.first?.confirmed == true,
                    null,
                    inviteCode = contactInfo.first?.code,
                    invitePrice = null,
                    null,
                    contactInfo.second
                )
            }

            newContactList.forEach { newContact ->
                if (newContact.inviteCode != null) {
                    updateNewContactInvited(newContact)
                } else {
                    createNewContact(newContact)
                }
            }

            callback?.let { nnCallback ->
                nnCallback()
            }
        }
    }

    override fun onRestoreMessages() {
        restoreProcessState.value = RestoreProcessState.RestoreMessages
    }

    override fun onUpsertTribes(
        tribes: List<Pair<String?, Boolean?>>,
        isProductionEnvironment: Boolean,
        callback: (() -> Unit)?
    )  {
        if (tribes.isEmpty()) {
            callback?.let { nnCallback ->
                nnCallback()
            }
            return
        }

        applicationScope.launch(io) {
            val total = tribes.count();
            var index = 0

            val tribeList = tribes.mapNotNull { tribes ->
                try {
                    Pair(
                        tribes.first?.toMsgSender(moshi),
                        tribes.second
                    )
                } catch (e: Exception) {
                    null
                }
            }

            tribeList.forEach { tribe ->
                val isAdmin = (tribe.first?.role == 0 && tribe.second == true)
                tribe.first?.let {
                    joinTribeOnRestoreAccount(it, isAdmin, isProductionEnvironment) {
                        if (index == total - 1) {
                            callback?.let { nnCallback ->
                                nnCallback()
                            }
                        } else {
                            index += 1
                        }
                    }
                } ?: run {
                    if (index == total - 1) {
                        callback?.let { nnCallback ->
                            nnCallback()
                        }
                    } else {
                        index += 1
                    }
                }
            }
        }
    }

    override fun onNewBalance(balance: Long) {
        applicationScope.launch(io) {

            balanceLock.withLock {
                accountBalanceStateFlow.value = balance.toNodeBalance()
                networkRefreshBalance.value = balance

                authenticationStorage.putString(
                    REPOSITORY_LIGHTNING_BALANCE,
                    balance.toString()
                )
            }
        }
    }

    override fun onSignedChallenge(sign: String) {
        connectionManagerState.value = OwnerRegistrationState.SignedChallenge(sign)
    }

    override fun onInitialTribe(tribe: String, isProductionEnvironment: Boolean) {
        applicationScope.launch(io) {
            val (host, tribePubKey) = extractUrlParts(tribe)

            if (host == null || tribePubKey == null) {
                return@launch
            }

            networkQueryChat.getTribeInfo(ChatHost(host), LightningNodePubKey(tribePubKey), isProductionEnvironment)
                .collect { loadResponse ->
                    when (loadResponse) {
                        is LoadResponse.Loading -> {}
                        is Response.Error -> {}
                        is Response.Success -> {
                            val queries = coreDB.getSphinxDatabaseQueries()

                            connectManager.joinToTribe(
                                host,
                                tribePubKey,
                                loadResponse.value.route_hint,
                                loadResponse.value.private ?: false,
                                accountOwner.value?.alias?.value ?: "unknown",
                                loadResponse.value.getPriceToJoinInSats()
                            )

                            // TribeId is set from LONG.MAX_VALUE and decremented by 1 for each new tribe
                            val tribeId = queries.chatGetLastTribeId().executeAsOneOrNull()
                                ?.let { it.MIN?.minus(1) }
                                ?: (Long.MAX_VALUE)

                            val now: String = DateTime.nowUTC()

                            val newTribe = Chat(
                                id = ChatId(tribeId),
                                uuid = ChatUUID(tribePubKey),
                                name = ChatName(loadResponse.value.name ?: "unknown"),
                                photoUrl = loadResponse.value.img?.toPhotoUrl(),
                                type = ChatType.Tribe,
                                status = ChatStatus.Approved,
                                contactIds = listOf(ContactId(0), ContactId(tribeId)),
                                isMuted = ChatMuted.False,
                                createdAt = now.toDateTime(),
                                groupKey = null,
                                host = ChatHost(host),
                                pricePerMessage = loadResponse.value.getPricePerMessageInSats().toSat(),
                                escrowAmount = loadResponse.value.getEscrowAmountInSats().toSat(),
                                unlisted = ChatUnlisted.False,
                                privateTribe = ChatPrivate.False,
                                ownerPubKey = LightningNodePubKey(tribePubKey),
                                seen = Seen.False,
                                metaData = null,
                                myPhotoUrl = null,
                                myAlias = null,
                                pendingContactIds = emptyList(),
                                latestMessageId = null,
                                contentSeenAt = null,
                                pinedMessage = loadResponse.value.pin?.toMessageUUID(),
                                notify = NotificationLevel.SeeAll,
                                secondBrainUrl = null,
                                timezoneEnabled = null,
                                timezoneIdentifier = null,
                                remoteTimezoneIdentifier = null,
                                timezoneUpdated = null
                            )

                            chatLock.withLock {
                                queries.transaction {
                                    upsertNewChat(
                                        newTribe,
                                        moshi,
                                        SynchronizedMap<ChatId, Seen>(),
                                        queries,
                                        null,
                                        accountOwner.value?.nodePubKey
                                    )
                                }
                            }
                        }
                    }
                }
        }
    }

    override fun onLastReadMessages(lastReadMessages: String) {
        applicationScope.launch(io) {
            val queries = coreDB.getSphinxDatabaseQueries()

            val lastReadMessagesMap = lastReadMessages.toLastReadMap(moshi)
            val pubKeys = lastReadMessagesMap?.keys

            val contactPubkey = pubKeys?.map { it.toLightningNodePubKey() }
            val tribePubKey = pubKeys?.map { it.toChatUUID() }

            val contacts = contactPubkey?.filterNotNull()?.let { queries.contactGetAllByPubKeys(it).executeAsList() }
            val tribes = tribePubKey?.filterNotNull()?.let { queries.chatGetAllByUUIDS(it).executeAsList() }

            // Create a new map for mapping chatId to lastMsgIndex
            val chatIdToLastMsgIndexMap = mutableMapOf<ChatId, MessageId>()

            contacts?.forEach { contact ->
                val lastMsgIndex = lastReadMessagesMap.get(contact.node_pub_key?.value)
                if (lastMsgIndex != null) {
                    chatIdToLastMsgIndexMap[ChatId(contact.id.value)] = MessageId(lastMsgIndex)
                }
            }

            tribes?.forEach { tribe ->
                val lastMsgIndex = lastReadMessagesMap.get(tribe.uuid.value)
                if (lastMsgIndex != null) {
                    chatIdToLastMsgIndexMap[tribe.id] = MessageId(lastMsgIndex)
                }
            }

            messageLock.withLock {
                queries.transaction {
                    chatIdToLastMsgIndexMap.forEach { (chatId, lastMsgIndex) ->
                        queries.messageUpdateSeenByChatIdAndId(chatId, lastMsgIndex)
                    }
                }
            }

            chatLock.withLock {
                chatIdToLastMsgIndexMap.forEach { (chatId, lastMsgIndex) ->
                    queries.chatUpdateSeenByLastMessage(chatId, lastMsgIndex)
                }
            }
        }
    }

    override fun onUpdateMutes(mutes: String) {
        applicationScope.launch(io) {
            val queries = coreDB.getSphinxDatabaseQueries()

            val mutesMap = mutes.toMuteLevelsMap(moshi)
            val pubKeys = mutesMap?.keys

            val contactPubkey = pubKeys?.map { it.toLightningNodePubKey() }
            val tribePubKey = pubKeys?.map { it.toChatUUID() }

            val contacts = contactPubkey?.filterNotNull()?.let { queries.contactGetAllByPubKeys(it).executeAsList() }
            val tribes = tribePubKey?.filterNotNull()?.let { queries.chatGetAllByUUIDS(it).executeAsList() }

            val notificationMap = mutableMapOf<ChatId, NotificationLevel>()

            contacts?.forEach { contact ->
                mutesMap[contact.node_pub_key?.value]?.let { level ->
                    notificationMap[ChatId(contact.id.value)] = when (level) {
                        NotificationLevel.SEE_ALL -> NotificationLevel.SeeAll
                        NotificationLevel.ONLY_MENTIONS -> NotificationLevel.OnlyMentions
                        NotificationLevel.MUTE_CHAT -> NotificationLevel.MuteChat
                        else -> NotificationLevel.Unknown(level)
                    }
                }
            }

            tribes?.forEach { tribe ->
                mutesMap[tribe.uuid.value]?.let { level ->
                    notificationMap[ChatId(tribe.id.value)] = when (level) {
                        NotificationLevel.SEE_ALL -> NotificationLevel.SeeAll
                        NotificationLevel.ONLY_MENTIONS -> NotificationLevel.OnlyMentions
                        NotificationLevel.MUTE_CHAT -> NotificationLevel.MuteChat
                        else -> NotificationLevel.Unknown(level)
                    }
                }
            }

            chatLock.withLock {
                queries.transaction {
                    notificationMap.forEach { (chatId, level) ->
                        queries.chatUpdateNotificationLevel(level, chatId)
                    }
                }
            }
        }
    }

    override fun onGetNodes() {
        connectionManagerState.value = OwnerRegistrationState.GetNodes
    }

    override fun listenToOwnerCreation(callback: () -> Unit) {
        applicationScope.launch(mainImmediate) {
            accountOwner.filter { contact ->
                contact != null && !contact.routeHint?.value.isNullOrEmpty()
            }
                .map { true }
                .first()

            withContext(dispatchers.mainImmediate) {
                delay(1000L)
                callback.invoke()
            }
        }
    }

    override fun onConnectManagerError(error: ConnectManagerError) {
        connectManagerErrorState.value = error
    }

    override fun onRestoreProgress(progress: Int) {
        restoreProgress.value = progress
    }

    override fun onRestoreFinished() {
        val messageId = restoreMinIndex.value?.let { MessageId(it) }
        if (messageId != null) {
            applicationScope.launch(io) {
                getMessageById(messageId).collect { message ->
                    if (message != null) {
                        restoreMinIndex.value = null
                    }
                }
            }
        }
    }

    override fun updatePaidInvoices() {
        applicationScope.launch(io) {
            val queries = coreDB.getSphinxDatabaseQueries()
            queries.messageGetAllPayments().executeAsList().forEach { message ->
                messageLock.withLock {
                    queries.messageUpdateInvoiceAsPaidByPaymentHash(message.payment_hash)
                }
            }
        }
    }

    // Messaging Callbacks
    override fun onMessage(
        msg: String,
        msgSender: String,
        msgType: Int,
        msgUuid: String,
        msgIndex: String,
        msgTimestamp: Long?,
        sentTo: String,
        amount: Long?,
        fromMe: Boolean?,
        tag: String?,
        date: Long?,
        isRestore: Boolean
    ) {
        applicationScope.launch(io) {
            try {
                if (msgIndex.toLong() == 2171L) {
                    LOG.d(TAG, "onBountyPayment: ${msg}")
                }

                val messageType = msgType.toMessageType()

                msgSender.toMsgSenderNull(moshi)?.let { nnMessageSender ->
                    val contactTribePubKey = if (fromMe == true) {
                        sentTo
                    } else {
                        nnMessageSender.pubkey
                    }

                    when (messageType) {
                        is MessageType.ContactKeyRecord -> {
                            if (!isRestore) {
                                saveNewContactRegistered(msgSender, date)
                            }
                        }
                        else -> {
                            val message = if (msg.isNotEmpty()) msg.toMsg(moshi) else Msg(
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null
                            )

                            when (messageType) {
                                is MessageType.Purchase.Processing -> {
                                    amount?.toSat()?.let { paidAmount ->
                                        sendMediaKeyOnPaidPurchase(
                                            message,
                                            nnMessageSender,
                                            paidAmount
                                        )
                                    }
                                }
                                is MessageType.ContactKeyConfirmation -> {
                                    saveNewContactRegistered(msgSender, date)
                                }
                                is MessageType.ContactKey -> {
                                    saveNewContactRegistered(msgSender, date)
                                }
                                is MessageType.Delete -> {
                                    msg.toMsg(moshi).replyUuid?.toMessageUUID()?.let { replyUuid ->
                                        deleteMqttMessage(replyUuid)
                                    }
                                }
                                else -> {}
                            }

                            val messageId = if (msgIndex.isNotEmpty()) MessageId(msgIndex.toLong()) else return@launch
                            val messageUuid = msgUuid.toMessageUUID() ?: return@launch
                            val originalUUID = message.originalUuid?.toMessageUUID()
                            val timestamp = msgTimestamp?.toDateTime()
                            val date = message.date?.toDateTime()
                            val paymentRequest = message.invoice?.toLightningPaymentRequestOrNull()
                            val bolt11 = paymentRequest?.let { Bolt11.decode(it) }
                            val paymentHash = paymentRequest?.let {
                                connectManager.retrievePaymentHash(it.value)?.toLightningPaymentHash()
                            }
                            val msgTag = tag?.toTagMessage()

                            upsertMqttMessage(
                                message,
                                nnMessageSender,
                                contactTribePubKey,
                                messageType,
                                messageUuid,
                                messageId,
                                message.amount?.milliSatsToSats(),
                                originalUUID,
                                timestamp,
                                date,
                                fromMe ?: false,
                                amount?.toSat(),
                                paymentRequest,
                                paymentHash,
                                bolt11,
                                msgTag,
                                isRestore
                            )
                        }
                    }
                } ?: run {
                    val message = if (msg.isNotEmpty()) msg.toMsg(moshi) else Msg(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                    )

                    if (msgIndex.isNotEmpty() && message.content?.isNotEmpty() == true) {
                        val messageId = MessageId(msgIndex.toLong())

                        upsertGenericPaymentMsg(
                            msg = message,
                            msgType = msgType.toMessageType(),
                            msgIndex = messageId,
                            msgAmount = amount?.toSat(),
                            timestamp = msgTimestamp?.toDateTime(),
                            paymentHash = message.paymentHash?.toLightningPaymentHash()
                        )
                    }
                }
            } catch (e: Exception) {
                LOG.e(TAG, "onMessage: ${e.message}", e)
            }
        }
    }

    override fun onMessageTagAndUuid(tag: String?, msgUUID: String, provisionalId: Long) {
        applicationScope.launch(io) {
            val queries = coreDB.getSphinxDatabaseQueries()
            val tagMessage = tag?.let { TagMessage(it) }

            // messageUpdateTagAndUUID also updates the Status to CONFIRMED
            messageLock.withLock {
                queries.messageUpdateTagAndUUID(tagMessage, MessageUUID(msgUUID), MessageId(provisionalId))
            }
        }
    }

    override fun onMessagesCounts(msgsCounts: String) {
        try {
            msgsCounts.toMsgsCounts(moshi)?.let {
                restoreProcessState.value = RestoreProcessState.MessagesCounts(it)
                connectManager.saveMessagesCounts(it)
            }
        } catch (e: Exception) {
            LOG.e(TAG, "onMessagesCounts: ${e.message}", e)
        }
    }

    override fun onSentStatus(sentStatus: String) {
        applicationScope.launch(io) {
            val newSentStatus = sentStatus.toNewSentStatus(moshi)
            val queries = coreDB.getSphinxDatabaseQueries()

            if (newSentStatus.tag == processingInvoice.value?.second) {
                if (newSentStatus.isFailedMessage()) {
                    processingInvoice.value?.first?.toLightningPaymentRequestOrNull()?.let {
                        payInvoiceFromLSP(it)
                    }
                }
                processingInvoice.value = null
            } else {
                if (newSentStatus.isFailedMessage()) {
                    queries.messageUpdateStatusAndPaymentHashByTag(
                        MessageStatus.Failed,
                        newSentStatus.payment_hash?.toLightningPaymentHash(),
                        newSentStatus.message?.toErrorMessage(),
                        newSentStatus.tag?.toTagMessage()
                    )
                } else {
                    queries.messageUpdateStatusAndPaymentHashByTag(
                        MessageStatus.Received,
                        newSentStatus.payment_hash?.toLightningPaymentHash(),
                        newSentStatus.message?.toErrorMessage(),
                        newSentStatus.tag?.toTagMessage()
                    )

                    // Check if web view payment hash matches
                    if (newSentStatus.payment_hash == webViewPaymentHash.value) {
                        webViewPreImage.value = newSentStatus.preimage
                        webViewPaymentHash.value = null
                    }
                }
            }
        }
    }

    override fun onMessageTagList(tags: String) {
        applicationScope.launch(io) {
            val queries = coreDB.getSphinxDatabaseQueries()
            val tagsList = tags.toTagsList(moshi)

            queries.transaction {
                tagsList?.forEach { tag ->
                    tag.status?.toMessageStatus()?.let { messageStatus ->
                        queries.messageUpdateStatusByTag(
                            messageStatus,
                            tag.error?.toErrorMessage(),
                            tag.tag?.toTagMessage()
                        )
                    }
                }
            }
        }
    }

    override fun onRestoreMinIndex(minIndex: Long) {
        restoreMinIndex.value = minIndex
    }

    // Tribe Management Callbacks
    override fun onNewTribeCreated(newTribe: String) {
        applicationScope.launch(io) {
            val queries = coreDB.getSphinxDatabaseQueries()
            val newCreateTribe = newTribe.toNewCreateTribe(moshi)
            newCreateTribe.pubkey?.let { tribePubKey ->

                val existingTribe = tribePubKey.toChatUUID()?.let { getChatByUUID(it) }?.firstOrNull()
                // TribeId is set from LONG.MAX_VALUE and decremented by 1 for each new tribe
                val tribeId = existingTribe?.id?.value ?: queries.chatGetLastTribeId().executeAsOneOrNull()?.let { it.MIN?.minus(1) }
                    ?: (Long.MAX_VALUE)
                val now: String = DateTime.nowUTC()

                val chatTribe = Chat(
                    id = ChatId(tribeId),
                    uuid = ChatUUID(tribePubKey),
                    name = ChatName(newCreateTribe.name),
                    photoUrl = newCreateTribe.img?.toPhotoUrl(),
                    type = ChatType.Tribe,
                    status = ChatStatus.Approved,
                    contactIds = listOf(ContactId(0), ContactId(tribeId)),
                    isMuted = ChatMuted.False,
                    createdAt = newCreateTribe.created?.toDateTime() ?: now.toDateTime(),
                    groupKey = existingTribe?.groupKey,
                    host = existingTribe?.host,
                    pricePerMessage = newCreateTribe.getPricePerMessageInSats().toSat(),
                    escrowAmount = newCreateTribe.getEscrowAmountInSats().toSat(),
                    unlisted = if (newCreateTribe.unlisted == true) ChatUnlisted.True else ChatUnlisted.False,
                    privateTribe = if (newCreateTribe.private == true) ChatPrivate.True else ChatPrivate.False,
                    ownerPubKey = accountOwner.value?.nodePubKey,
                    seen = Seen.False,
                    metaData = existingTribe?.metaData,
                    myPhotoUrl = accountOwner.value?.photoUrl,
                    myAlias = ChatAlias(newCreateTribe.owner_alias),
                    pendingContactIds = emptyList(),
                    latestMessageId = existingTribe?.latestMessageId,
                    contentSeenAt = existingTribe?.contentSeenAt,
                    pinedMessage = existingTribe?.pinedMessage,
                    notify = NotificationLevel.SeeAll,
                    secondBrainUrl = existingTribe?.secondBrainUrl,
                    timezoneEnabled = existingTribe?.timezoneEnabled,
                    timezoneIdentifier = existingTribe?.timezoneIdentifier,
                    remoteTimezoneIdentifier = existingTribe?.remoteTimezoneIdentifier,
                    timezoneUpdated = existingTribe?.timezoneUpdated
                )

                chatLock.withLock {
                    queries.transaction {
                        upsertNewChat(
                            chatTribe,
                            moshi,
                            SynchronizedMap<ChatId, Seen>(),
                            queries,
                            null,
                            accountOwner.value?.nodePubKey
                        )
                    }
                }
            }
        }
    }

    override fun onTribeMembersList(tribeMembers: String) {
        applicationScope.launch(mainImmediate) {
            try {
                tribeMembers.toTribeMembersList(moshi)?.let { members ->
                    tribeMembersState.value = members
                }
            } catch (e: Exception) {
            }
        }
    }

    // Invoice and Payment Management Callbacks

    override fun onPayments(payments: String) {
        applicationScope.launch(io) {
            val paymentsJson = payments.toPaymentsList(moshi)

            val paymentsReceived = paymentsJson?.mapNotNull {
                it.msg_idx?.let { msgId ->
                    MessageId(msgId)
                }
            }

            val paymentsSent = paymentsJson?.mapNotNull {
                it.rhash?.let { hash ->
                    LightningPaymentHash(hash)
                }
            }

            val paymentsReceivedMsgs = paymentsReceived?.let {
                getMessagesByIds(it).firstOrNull()
            }

            val paymentsSentMsgs = paymentsSent?.let {
                getMessagesByPaymentHashes(it).firstOrNull()
            }

            // Combine all retrieved messages from DB
            val combinedMessages: List<Message?> = paymentsReceivedMsgs.orEmpty() + paymentsSentMsgs.orEmpty()

            // Generate TransactionDto from the combinedMessages list or from the raw payments data
            val transactionDtoList = paymentsJson?.map { payment ->
                // Try to find corresponding DB message first
                val dbMessage = combinedMessages.firstOrNull {
                    it?.id?.value == payment.msg_idx || it?.paymentHash?.value == payment.rhash
                }

                dbMessage?.takeIf { it.type !is MessageType.Invoice }?.let { message ->
                    // If found in DB, build TransactionDto using DB information
                    TransactionDto(
                        id = message.id.value,
                        chat_id = message.chatId.value,
                        type = message.type.value,
                        sender = message.sender.value,
                        sender_alias = message.senderAlias?.value,
                        receiver = message.receiver?.value,
                        amount = message.amount.value,
                        payment_hash = message.paymentHash?.value,
                        payment_request = message.paymentRequest?.value,
                        date = message.date,
                        reply_uuid = message.replyUUID?.value,
                        error_message = message.errorMessage?.value,
                        message_content = message.messageContentDecrypted?.value
                    )
                } ?: run {
                    // If not found in DB, create TransactionDto with available information from the Payment object
                    val isIncoming = payment.msg_idx != null

                    TransactionDto(
                        id = payment.msg_idx ?: 0L,
                        chat_id = null,
                        type = MessageType.DirectPayment.value,
                        sender = if ((isIncoming)) -1 else 0,
                        sender_alias = null,
                        receiver = if ((isIncoming)) 0 else -1,
                        amount = payment.amt_msat?.milliSatsToSats()?.value ?: 0L,
                        payment_hash = payment.rhash,
                        payment_request = null,
                        date = payment.ts?.toDateTime(),
                        reply_uuid = null,
                        error_message = payment.error,
                        message_content = null
                    )
                }
            }.orEmpty()

            // Sort the transactions by date and set the result to the state
            transactionDtoState.value = transactionDtoList.sortedByDescending { it.date?.value }.distinct()
        }
    }

    override fun onNetworkStatusChange(
        isConnected: Boolean,
        isLoading: Boolean
    ) {
        if (isConnected) {
            networkStatus.value = NetworkStatus.Connected
        } else if (isLoading) {
            networkStatus.value = NetworkStatus.Loading
        } else {
            networkStatus.value = NetworkStatus.Disconnected
            reconnectMqtt()
        }
    }
    override fun onNewInviteCreated(
        nickname: String,
        inviteString: String,
        inviteCode: String,
        sats: Long
    ) {
        applicationScope.launch(mainImmediate) {
            val newInvitee = NewContact(
                contactAlias = nickname.toContactAlias(),
                lightningNodePubKey = null,
                lightningRouteHint = null,
                photoUrl = null,
                confirmed = false,
                inviteString = inviteString,
                inviteCode = inviteCode,
                invitePrice = sats.toSat(),
                inviteStatus = InviteStatus.Pending,
                null
            )
            createNewContact(newInvitee)
        }
    }

    override fun onPerformDelay(delay: Long, callback: () -> Unit) {
        applicationScope.launch(mainImmediate) {
            delay(delay)
            callback.invoke()
        }
    }

    override fun reconnectMqtt() {
        applicationScope.launch(mainImmediate) {
            delay(1000L)
            connectManager.reconnectWithBackOff()
        }
    }

    fun extractUrlParts(url: String): Pair<String?, String?> {
        val cleanUrl = url.replace(Regex("^[a-zA-Z]+://"), "")
        val separatorIndex = cleanUrl.indexOf("/")

        if (separatorIndex == -1) return null to null
        val host = cleanUrl.substring(0, separatorIndex).takeIf { it.isNotEmpty() }
        val tribePubKey = cleanUrl.substring(separatorIndex + 1).split("/").lastOrNull()?.takeIf { it.isNotEmpty() }

        return host to tribePubKey
    }

    override suspend fun upsertMqttMessage(
        msg: Msg,
        msgSender: MsgSender,
        contactTribePubKey: String,
        msgType: MessageType,
        msgUuid: MessageUUID,
        msgIndex: MessageId,
        msgAmount: Sat?,
        originalUuid: MessageUUID?,
        timestamp: DateTime?,
        date: DateTime?,
        fromMe: Boolean,
        realPaymentAmount: Sat?,
        paymentRequest: LightningPaymentRequest?,
        paymentHash: LightningPaymentHash?,
        bolt11: Bolt11?,
        tag: TagMessage?,
        isRestore: Boolean
    ) {
        val queries = coreDB.getSphinxDatabaseQueries()
        val contact = contactTribePubKey.toLightningNodePubKey()?.let { getContactByPubKey(it).firstOrNull() }
        val owner = accountOwner.value
        val chatTribe = contactTribePubKey.toChatUUID()?.let { getChatByUUID(it).firstOrNull() }
        var messageMedia: MessageMediaDbo? = null
        val isTribe = contact == null

        originalUuid?.let { uuid ->
            queries.messageUpdateUUIDByUUID(msgUuid, uuid )
        }

        // On Conversation ChatId is contactId defined by the bindings,
        // tribes use the auto-generated chatId
        val chatId = when {
            contact?.id?.value != null -> contact.id.value
            chatTribe?.id?.value != null -> chatTribe.id.value
            else -> 0L
        }

        val existingMessage = queries.messageGetByUUID(msgUuid).executeAsOneOrNull()
        val messageId = existingMessage?.id

        if (fromMe && messageId?.value != null && messageId.value < 0) {
            val existingMessageMedia = messageId.let {
                queries.messageMediaGetById(it).executeAsOneOrNull()
            }?.copy(id = msgIndex)

            existingMessageMedia?.let {
                messageLock.withLock {
                    queries.messageMediaDeleteById(messageId)
                }
                messageMedia = existingMessageMedia
            }
        } else {
            msg.mediaToken?.toMediaToken()?.let { mediaToken ->

                messageMedia = MessageMediaDbo(
                    msgIndex,
                    ChatId(chatId),
                    msg.mediaKey?.toMediaKey(),
                    msg.mediaKey?.toMediaKeyDecrypted(),
                    msg.mediaType?.toMediaType() ?: MediaType.Unknown(""),
                    mediaToken,
                    null,
                    null
                )
            }
        }

        messageLock.withLock {
            queries.messageDeleteByUUID(msgUuid)
        }

        var senderAlias = msgSender.alias?.toSenderAlias()

        if (msgType.isMemberApprove() || msgType.isMemberReject()) {
            msg.replyUuid?.toReplyUUID()?.let { replyUUID ->
                queries.messageGetByUUID(MessageUUID(replyUUID.value)).executeAsOneOrNull()?.let { requestMsg ->
                    requestMsg.sender_alias?.let {
                        senderAlias = it
                    }
                }
            }
        }

        val hasPaymentRequest = paymentRequest != null || existingMessage?.payment_request != null

        val status = when {
            fromMe && hasPaymentRequest -> MessageStatus.Pending
            fromMe && !hasPaymentRequest -> MessageStatus.Confirmed
            !fromMe && hasPaymentRequest -> MessageStatus.Pending
            else -> MessageStatus.Received
        }

        val isTribeBoost = isTribe && msgType is MessageType.Boost
        val amount = if (fromMe || isTribeBoost) msgAmount else realPaymentAmount

        val now = DateTime.nowUTC().toDateTime()
        val messageDate = if (isTribe) date ?: now else timestamp ?: now

        val newMessage = NewMessage(
            id = msgIndex,
            uuid = msgUuid,
            chatId = ChatId(chatId),
            type = msgType,
            sender = if (fromMe) ContactId(0) else contact?.id ?: ContactId(chatId) ,
            receiver = ContactId(0),
            amount = bolt11?.getSatsAmount() ?: existingMessage?.amount ?: amount ?: Sat(0L),
            paymentRequest = existingMessage?.payment_request ?: paymentRequest,
            paymentHash = existingMessage?.payment_hash ?: msg.paymentHash?.toLightningPaymentHash() ?: paymentHash,
            date = messageDate,
            expirationDate = existingMessage?.expiration_date ?: bolt11?.getExpiryTime()?.toDateTime(),
            messageContent = null,
            status = status,
            seen = Seen.False,
            senderAlias = senderAlias,
            senderPic = msgSender.photo_url?.toPhotoUrl(),
            originalMUID = null,
            replyUUID = existingMessage?.reply_uuid ?: msg.replyUuid?.toReplyUUID(),
            flagged = Flagged.False,
            recipientAlias = null,
            recipientPic = null,
            person = null,
            threadUUID = existingMessage?.thread_uuid ?: msg.threadUuid?.toThreadUUID(),
            errorMessage = null,
            tagMessage = existingMessage?.tag_message ?: tag,
            isPinned = false,
            messageContentDecrypted = if (msg.content?.isNotEmpty() == true) MessageContentDecrypted(msg.content!!) else null,
            messageDecryptionError = false,
            messageDecryptionException = null,
            messageMedia = messageMedia?.let { MessageMediaDboWrapper(it) },
            feedBoost = null,
            callLinkMessage = null,
            podcastClip = null,
            giphyData = null,
            reactions = null,
            purchaseItems = null,
            replyMessage = null,
            thread = null,
            remoteTimezoneIdentifier = if (isTribe) msg.metadata?.toMessageMetadata(moshi)?.tz?.toRemoteTimezoneIdentifier() else null
        )

        contact?.id?.let { contactId ->
            if (!fromMe) {
                val lastMessageIndex = getLastMessage().firstOrNull()?.id?.value
                val newMessageIndex = msgIndex.value

                if (lastMessageIndex != null) {
                    if (lastMessageIndex < newMessageIndex) {
                        contactLock.withLock {
                            msgSender.photo_url?.takeIf { it.isNotEmpty() && it != contact.photoUrl?.value }
                                ?.let {
                                    queries.contactUpdatePhotoUrl(it.toPhotoUrl(), contactId)
                                }
                            msgSender.alias?.takeIf { it.isNotEmpty() && it != contact.alias?.value }
                                ?.let {
                                    queries.contactUpdateAlias(it.toContactAlias(), contactId)
                                }
                        }
                    }
                }
            } else {
                contactLock.withLock {
                    if (owner != null) {
                        if (owner.alias?.value == null) {
                            msgSender.alias?.takeIf { it.isNotEmpty() && it != owner.alias?.value }
                                ?.let {
                                    queries.contactUpdateAlias(it.toContactAlias(), owner.id)

                                    connectManager.ownerInfoStateFlow.value?.let { ownerInfo ->
                                        connectManager.setOwnerInfo(
                                            ownerInfo.copy(alias = it)
                                        )
                                    }
                                }
                        }

                        if (owner.photoUrl?.value == null) {
                            msgSender.photo_url?.takeIf { it.isNotEmpty() && it != owner.photoUrl?.value }
                                ?.let {
                                    queries.contactUpdatePhotoUrl(it.toPhotoUrl(), owner.id)

                                    connectManager.ownerInfoStateFlow.value?.let { ownerInfo ->
                                        connectManager.setOwnerInfo(
                                            ownerInfo.copy(picture = it)
                                        )
                                    }
                                }
                        }
                    }
                }
            }
        }

        if (msgType is MessageType.Payment) {
            queries.messageUpdateInvoiceAsPaidByPaymentHash(
                msg.paymentHash?.toLightningPaymentHash()
            )
        }

        messageLock.withLock {
            queries.transaction {
                upsertNewMessage(newMessage, queries, messageMedia?.file_name)

                updateChatNewLatestMessage(
                    newMessage,
                    ChatId(chatId),
                    latestMessageUpdatedTimeMap,
                    queries
                )
            }
        }

        chatLock.withLock {
            queries.chatUpdateSeen(Seen.False, ChatId(chatId))
        }

        if (!fromMe) {
            msg.metadata?.toMessageMetadata(moshi)?.tz?.let {
                if (!isTribe) {
                    updateChatRemoteTimezoneIdentifier(
                        remoteTimezoneIdentifier = it.toRemoteTimezoneIdentifier(),
                        chatId = ChatId(chatId),
                        isRestore = isRestore
                    )
                }
            }
        }
    }

    private suspend fun upsertGenericPaymentMsg(
        msg: Msg,
        msgType: MessageType,
        msgIndex: MessageId,
        msgAmount: Sat?,
        timestamp: DateTime?,
        paymentHash: LightningPaymentHash?,
    ) {
        val queries = coreDB.getSphinxDatabaseQueries()

        val newMessage = NewMessage(
            id = msgIndex,
            chatId = ChatId(ChatId.NULL_CHAT_ID.toLong()),
            type = msgType,
            sender = ContactId(-1),
            receiver = ContactId(0),
            amount = msgAmount ?: Sat(0L),
            paymentHash = paymentHash,
            date = timestamp ?: DateTime.nowUTC().toDateTime(),
            status = MessageStatus.Received,
            seen = Seen.False,
            flagged = Flagged.False,
            messageContentDecrypted = if (msg.content?.isNotEmpty() == true) MessageContentDecrypted(msg.content!!) else null,
            messageDecryptionError = false,
        )

        messageLock.withLock {
            queries.transaction {
                upsertNewMessage(newMessage, queries, null)
            }
        }
    }

    override suspend fun deleteMqttMessage(messageUuid: MessageUUID) {
        val queries = coreDB.getSphinxDatabaseQueries()

        messageLock.withLock {
            queries.messageUpdateStatusByUUID(MessageStatus.Deleted, messageUuid)
        }
    }

    override suspend fun fetchDeletedMessagesOnDb() {
        val queries = coreDB.getSphinxDatabaseQueries()
        val messagesToDelete = getDeletedMessages().firstOrNull()

        if (messagesToDelete != null) {
            val uuidToDelete = messagesToDelete.map { it.replyUUID?.value?.toMessageUUID() }
            queries.messageUpdateMessagesStatusByUUIDS(MessageStatus.Deleted, uuidToDelete)
            // after this line, the messages of type 17 should be deleted on the Db
        }
    }

    private fun sendNewMessage(
        contact: String,
        messageContent: String,
        attachmentInfo: AttachmentInfo?,
        mediaToken: MediaToken?,
        mediaKey: MediaKey?,
        messageType: MessageType?,
        provisionalId: MessageId?,
        amount: Sat?,
        replyUUID: ReplyUUID?,
        threadUUID: ThreadUUID?,
        isTribe: Boolean,
        memberPubKey: LightningNodePubKey?,
        metadata: String?
    ) {
        val newMessage = chat.sphinx.example.wrapper_mqtt.Message(
            messageContent,
            null,
            mediaToken?.value,
            mediaKey?.value,
            attachmentInfo?.mediaType?.value,
            replyUUID?.value,
            threadUUID?.value,
            memberPubKey?.value,
            null,
            metadata
        ).toJson(moshi)

        provisionalId?.value?.let {
            connectManager.sendMessage(
                newMessage,
                contact,
                it,
                messageType?.value ?: 0,
                amount?.value,
                isTribe
            )
        }
    }

    override suspend fun updateTimezoneEnabledStatus(
        isTimezoneEnabled: TimezoneEnabled,
        chatId: ChatId
    ) {
        val queries = coreDB.getSphinxDatabaseQueries()

        try {
            chatLock.withLock {
                queries.chatUpdateTimezoneEnabled(
                    timezone_enabled = isTimezoneEnabled,
                    id = chatId
                )
            }
        } catch (ex: Exception) {
            LOG.e(TAG, ex.printStackTrace().toString(), ex)
        }
    }

    override suspend fun updateTimezoneIdentifier(
        timezoneIdentifier: TimezoneIdentifier?,
        chatId: ChatId
    ) {
        val queries = coreDB.getSphinxDatabaseQueries()

        try {
            chatLock.withLock {
                queries.chatUpdateTimezoneIdentifier(
                    timezone_identifier = timezoneIdentifier,
                    id = chatId
                )
            }
        } catch (ex: Exception) {
            LOG.e(TAG, ex.printStackTrace().toString(), ex)
        }
    }

    override suspend fun updateTimezoneUpdated(
        timezoneUpdated: TimezoneUpdated,
        chatId: ChatId
    ) {
        updateTimezoneFlag(timezoneUpdated, chatId)
    }

    private suspend fun updateTimezoneFlag(
        timezoneUpdated: TimezoneUpdated,
        chatId: ChatId
    ) {
        val queries = coreDB.getSphinxDatabaseQueries()

        try {
            chatLock.withLock {
                queries.chatUpdateTimezoneUpdated(
                    timezone_updated = timezoneUpdated,
                    id = chatId
                )
            }
        } catch (ex: Exception) {
            LOG.e(TAG, ex.printStackTrace().toString(), ex)
        }
    }

    override suspend fun updateTimezoneUpdatedOnSystemChange() {
        val queries = coreDB.getSphinxDatabaseQueries()

        try {
            chatLock.withLock {
                queries.chatUpdateTimezoneUpdatedOnSystemChanged()
            }
        } catch (ex: Exception) {
            LOG.e(TAG, ex.printStackTrace().toString(), ex)
        }
    }

    override suspend fun updateChatRemoteTimezoneIdentifier(
        remoteTimezoneIdentifier: RemoteTimezoneIdentifier?,
        chatId: ChatId,
        isRestore: Boolean
    ) {
        val queries = coreDB.getSphinxDatabaseQueries()

        try {
            chatLock.withLock {
                if (isRestore) {
                    queries.chatUpdateRemoteTimezoneIdentifierIfNull(
                        remote_timezone_identifier = remoteTimezoneIdentifier,
                        id = chatId
                    )
                } else {
                    queries.chatUpdateRemoteTimezoneIdentifier(
                        remote_timezone_identifier = remoteTimezoneIdentifier,
                        id = chatId
                    )
                }
            }
        } catch (ex: Exception) {
            LOG.e(TAG, ex.printStackTrace().toString(), ex)
        }
    }

    private suspend fun joinTribeOnRestoreAccount(
        contactInfo: MsgSender,
        isAdmin: Boolean,
        isProductionEnvironment: Boolean,
        callback: (() -> Unit)? = null
    ) {
        val host = contactInfo.host ?: return

        withContext(dispatchers.io) {
            networkQueryChat.getTribeInfo(ChatHost(host), LightningNodePubKey(contactInfo.pubkey), isProductionEnvironment)
                .collect { loadResponse ->
                    when (loadResponse) {
                        is LoadResponse.Loading -> {}
                        is Response.Error -> {
                            callback?.let {nnCallback ->
                                nnCallback()
                            }
                        }
                        is Response.Success -> {
                            val queries = coreDB.getSphinxDatabaseQueries()

                            // TribeId is set from LONG.MAX_VALUE and decremented by 1 for each new tribe
                            val tribeId = queries.chatGetLastTribeId().executeAsOneOrNull()
                                ?.let { it.MIN?.minus(1) }
                                ?: (Long.MAX_VALUE)

                            val now: String = DateTime.nowUTC()

                            val newTribe = Chat(
                                id = ChatId(tribeId),
                                uuid = ChatUUID(contactInfo.pubkey),
                                name = ChatName(loadResponse.value.name),
                                photoUrl = loadResponse.value.img?.toPhotoUrl(),
                                type = ChatType.Tribe,
                                status = ChatStatus.Approved,
                                contactIds = listOf(ContactId(0), ContactId(tribeId)),
                                isMuted = ChatMuted.False,
                                createdAt = now.toDateTime(),
                                groupKey = null,
                                host = contactInfo.host?.toChatHost(),
                                pricePerMessage = loadResponse.value.getPricePerMessageInSats().toSat(),
                                escrowAmount = loadResponse.value.getEscrowAmountInSats().toSat(),
                                unlisted = loadResponse.value.unlisted?.toChatUnlisted() ?: ChatUnlisted.False,
                                privateTribe = loadResponse.value.private.toChatPrivate(),
                                ownerPubKey = if (isAdmin) accountOwner.value?.nodePubKey else LightningNodePubKey(contactInfo.pubkey),
                                seen = Seen.False,
                                metaData = null,
                                myPhotoUrl = null,
                                myAlias = null,
                                pendingContactIds = emptyList(),
                                latestMessageId = null,
                                contentSeenAt = null,
                                pinedMessage = loadResponse.value.pin?.toMessageUUID(),
                                notify = NotificationLevel.SeeAll,
                                secondBrainUrl = loadResponse.value.second_brain_url?.toSecondBrainUrl(),
                                timezoneEnabled = null,
                                timezoneIdentifier = null,
                                remoteTimezoneIdentifier = null,
                                timezoneUpdated = null
                            )

                            messageLock.withLock {
                                chatLock.withLock {
                                    queries.transaction {
                                        upsertNewChat(
                                            newTribe,
                                            moshi,
                                            SynchronizedMap<ChatId, Seen>(),
                                            queries,
                                            null,
                                            accountOwner.value?.nodePubKey
                                        )
                                    }
                                }
                            }

                            callback?.let {nnCallback ->
                                nnCallback()
                            }
                        }
                    }
                }
        }
    }

    override var updatedContactIds: MutableList<ContactId> = mutableListOf()

    override fun sendMediaKeyOnPaidPurchase(
        msg: Msg,
        contactInfo: MsgSender,
        paidAmount: Sat
    ) {
        applicationScope.launch {
            val queries = coreDB.getSphinxDatabaseQueries()
            val muid = msg.mediaToken?.toMediaToken()?.getMUIDFromMediaToken()

            muid?.value?.let { nnMuid ->
                val message = queries.messageGetByMuid(MessageMUID(nnMuid)).executeAsOneOrNull()
                val mediaMessage = message?.id?.let { queries.messageMediaGetById(it) }?.executeAsOneOrNull()

                val contact: Contact? = message?.chat_id?.let { chatId ->
                    getContactById(ContactId(chatId.value)).firstOrNull()
                }

                val messageType = if (message?.amount?.value == paidAmount.value) {
                    MessageType.Purchase.Accepted
                } else {
                    MessageType.Purchase.Denied
                }

                if (message != null && contact != null && mediaMessage != null) {

                    val currentProvisionalId: MessageId? = withContext(io) {
                        queries.messageGetLowestProvisionalMessageId().executeAsOneOrNull()
                    }
                    val provisionalId = MessageId((currentProvisionalId?.value ?: 0L) - 1)

                    // Message Accepted
                    val mediaMessageAccepted = mediaMessage.copy(media_key = null, media_key_decrypted = null, local_file = null)
                    val messageAccepted = message.copy(
                        type = messageType,
                        id = provisionalId
                    ).let { convertMessageDboToNewMessage(it, mediaMessageAccepted) }

                    chatLock.withLock {
                        messageLock.withLock {
                            withContext(io) {

                                queries.transaction {
                                    upsertNewMessage(messageAccepted, queries, null)
                                }

                                queries.transaction {
                                    updateChatNewLatestMessage(
                                        messageAccepted,
                                        message.chat_id,
                                        latestMessageUpdatedTimeMap,
                                        queries
                                    )
                                }
                            }
                        }
                    }

                    val mediaKey = if (messageType is MessageType.Purchase.Accepted) {
                        mediaMessage.media_key?.value
                    }
                    else {
                        null
                    }

                    val newPurchaseMessage = chat.sphinx.example.wrapper_mqtt.Message(
                        null,
                        null,
                        mediaMessage.media_token.value,
                        mediaKey,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                    ).toJson(moshi)

                    contact.let { nnContact ->
                        connectManager.sendMessage(
                            newPurchaseMessage,
                            contact.nodePubKey?.value ?: "",
                            provisionalId.value,
                            messageType.value,
                            null,
                        )
                    }
                }
            }
        }
    }



    override fun updatePaidMessageMediaKey(
        msg: Msg,
        contactInfo: MsgSender
    ) {
        applicationScope.launch {
            val queries = coreDB.getSphinxDatabaseQueries()
            val muid = msg.mediaToken?.toMediaToken()?.getMUIDFromMediaToken()

            muid?.value?.let { nnMuid ->
                val message = queries.messageGetByMuid(MessageMUID(nnMuid)).executeAsOneOrNull()
                val mediaKey = msg.mediaKey?.toMediaKeyDecrypted()

                message?.id?.let { queries.messageMediaUpdateMediaKeyDecrypted(mediaKey, it) }
            }
        }
    }

    /////////////
    /// Chats ///
    /////////////
    private val chatLock = Mutex()
    private val chatDboPresenterMapper: ChatDboPresenterMapper by lazy {
        ChatDboPresenterMapper(dispatchers)
    }

    override val getAllChats: Flow<List<Chat>> by lazy {
        flow {
            emitAll(
                coreDB.getSphinxDatabaseQueries().chatGetAll()
                    .asFlow()
                    .mapToList(io)
                    .map { chatDboPresenterMapper.mapListFrom(it) }
            )
        }
    }

    override val getAllContactChats: Flow<List<Chat>> by lazy {
        flow {
            emitAll(
                coreDB.getSphinxDatabaseQueries().chatGetAllContact()
                    .asFlow()
                    .mapToList(io)
                    .map { chatDboPresenterMapper.mapListFrom(it) }
            )
        }
    }

    override val getAllTribeChats: Flow<List<Chat>> by lazy {
        flow {
            emitAll(
                coreDB.getSphinxDatabaseQueries().chatGetAllTribe()
                    .asFlow()
                    .mapToList(io)
                    .map { chatDboPresenterMapper.mapListFrom(it) }
            )
        }
    }

    override suspend fun getAllChatsByIds(chatIds: List<ChatId>): List<Chat> {
        return coreDB.getSphinxDatabaseQueries()
            .chatGetAllByIds(chatIds)
            .executeAsList()
            .map { chatDboPresenterMapper.mapFrom(it) }
    }

    override fun getChatById(chatId: ChatId): Flow<Chat?> = flow {
        emitAll(
            coreDB.getSphinxDatabaseQueries().chatGetById(chatId)
                .asFlow()
                .mapToOneOrNull(io)
                .map { it?.let { chatDboPresenterMapper.mapFrom(it) } }
                .distinctUntilChanged()
        )
    }

    override fun getChatByUUID(chatUUID: ChatUUID): Flow<Chat?> = flow {
        emitAll(
            coreDB.getSphinxDatabaseQueries().chatGetByUUID(chatUUID)
                .asFlow()
                .mapToOneOrNull(io)
                .map { it?.let { chatDboPresenterMapper.mapFrom(it) } }
                .distinctUntilChanged()
        )
    }

    override fun getConversationByContactId(contactId: ContactId): Flow<Chat?> = flow {
        var ownerId: ContactId? = accountOwner.value?.id

        if (ownerId == null) {
            try {
                accountOwner.collect {
                    if (it != null) {
                        ownerId = it.id
                        throw Exception()
                    }
                }
            } catch (e: Exception) {
            }
            delay(25L)
        }

        emitAll(
            coreDB.getSphinxDatabaseQueries()
                .chatGetConversationForContact(
                    if (ownerId != null) {
                        listOf(ownerId!!, contactId)
                    } else {
                        listOf()
                    }
                )
                .asFlow()
                .mapToOneOrNull(io)
                .map { it?.let { chatDboPresenterMapper.mapFrom(it) } }
                .distinctUntilChanged()
        )
    }

    override fun getUnseenMessagesByChatId(chatId: ChatId): Flow<Long?> = flow {
        var ownerId: ContactId? = accountOwner.value?.id

        if (ownerId == null) {
            try {
                accountOwner.collect { contact ->
                    if (contact != null) {
                        ownerId = contact.id
                        throw Exception()
                    }
                }
            } catch (e: Exception) {
            }
            delay(25L)
        }

        emitAll(
            coreDB.getSphinxDatabaseQueries()
                .messageGetUnseenIncomingMessageCountByChatId(
                    ownerId ?: ContactId(-1),
                    chatId
                )
                .asFlow()
                .mapToOneOrNull(io)
                .distinctUntilChanged()
        )
    }

    override fun getUnseenMentionsByChatId(chatId: ChatId): Flow<Long?> = flow {
        var ownerId: ContactId? = accountOwner.value?.id

        if (ownerId == null) {
            try {
                accountOwner.collect { contact ->
                    if (contact != null) {
                        ownerId = contact.id
                        throw Exception()
                    }
                }
            } catch (e: Exception) {}
            delay(25L)
        }

        emitAll(
            coreDB.getSphinxDatabaseQueries()
                .messageGetUnseenIncomingMentionsCountByChatId(
                    ownerId ?: ContactId(-1),
                    chatId
                )
                .asFlow()
                .mapToOneOrNull(io)
                .distinctUntilChanged()
        )
    }

    override fun getUnseenActiveConversationMessagesCount(): Flow<Long?> = flow {
        val queries = coreDB.getSphinxDatabaseQueries()
        var ownerId: ContactId? = accountOwner.value?.id

        if (ownerId == null) {
            try {
                accountOwner.collect { contact ->
                    if (contact != null) {
                        ownerId = contact.id
                        throw Exception()
                    }
                }
            } catch (e: Exception) {
            }
            delay(25L)
        }

        val blockedContactIds = queries.contactGetBlocked().executeAsList().map { it.id }

        emitAll(
            queries
                .messageGetUnseenIncomingMessageCountByChatType(
                    ownerId ?: ContactId(-1),
                    blockedContactIds,
                    ChatType.Conversation
                )
                .asFlow()
                .mapToOneOrNull(io)
                .distinctUntilChanged()
        )
    }

    override fun getUnseenTribeMessagesCount(): Flow<Long?> = flow {
        var ownerId: ContactId? = accountOwner.value?.id

        if (ownerId == null) {
            try {
                accountOwner.collect { contact ->
                    if (contact != null) {
                        ownerId = contact.id
                        throw Exception()
                    }
                }
            } catch (e: Exception) {
            }
            delay(25L)
        }

        emitAll(
            coreDB.getSphinxDatabaseQueries()
                .messageGetUnseenIncomingMessageCountByChatType(
                    ownerId ?: ContactId(-1),
                    listOf(),
                    ChatType.Tribe
                )
                .asFlow()
                .mapToOneOrNull(io)
                .distinctUntilChanged()
        )
    }

    override fun getPaymentsTotalFor(feedId: FeedId): Flow<Sat?> = flow {
        emitAll(
            coreDB.getSphinxDatabaseQueries()
                .messageGetAmountSumForMessagesStartingWith(
                    "{\"feedID\":${feedId.value.toLongOrNull()}%",
                    "{\"feedID\":\"${feedId.value}\"%"
                )
                .asFlow()
                .mapToOneOrNull(io)
                .map { it?.SUM }
                .distinctUntilChanged()
        )
    }

    override fun streamFeedPayments(
        chatId: ChatId,
        feedId: String,
        feedItemId: String,
        currentTime: Long,
        amount: Sat?,
        playerSpeed: FeedPlayerSpeed?,
        destinations: List<FeedDestination>,
        clipMessageUUID: MessageUUID?,
        routerUrl: String?,
        routerPubKey: String?
    ) {
        if ((amount?.value ?: 0) <= 0 || destinations.isEmpty()) {
            return
        }

        applicationScope.launch(io) {
            val streamSatsText = StreamSatsText(
                feedId,
                feedItemId,
                currentTime,
            ).toJson(moshi)

            val totalSplit = destinations.sumOf { it.split.value }

            for (destination in destinations) {
                val destinationAmount = (amount?.value?.toDouble() ?: 0.0) * (destination.split.value / totalSplit)

                destination.address.value.toLightningNodePubKey()?.let { pubKey ->
                    sendKeySendWithRouting(
                        pubKey,
                        null,
                        destinationAmount.toLong().toSat()?.toMilliSat(),
                        routerUrl,
                        routerPubKey,
                        streamSatsText
                    )
                }
            }
        }
    }

    ////////////////
    /// Contacts ///
    ////////////////
    private val contactLock = Mutex()
    private val contactDboPresenterMapper: ContactDboPresenterMapper by lazy {
        ContactDboPresenterMapper(dispatchers)
    }
    private val inviteDboPresenterMapper: InviteDboPresenterMapper by lazy {
        InviteDboPresenterMapper(dispatchers)
    }

    private val inviteLock = Mutex()

    override val getAllContacts: Flow<List<Contact>> by lazy {
        flow {
            emitAll(
                coreDB.getSphinxDatabaseQueries().contactGetAll()
                    .asFlow()
                    .mapToList(io)
                    .map { contactDboPresenterMapper.mapListFrom(it) }
            )
        }
    }

    override val getAllNotBlockedContacts: Flow<List<Contact>> by lazy {
        flow {
            emitAll(
                coreDB.getSphinxDatabaseQueries().contactGetNotBlocked()
                    .asFlow()
                    .mapToList(io)
                    .map { contactDboPresenterMapper.mapListFrom(it) }
            )
        }
    }

    override val getAllInvites: Flow<List<Invite>> by lazy {
        flow {
            emitAll(
                coreDB.getSphinxDatabaseQueries().inviteGetAll()
                    .asFlow()
                    .mapToList(io)
                    .map { inviteDboPresenterMapper.mapListFrom(it) }
            )
        }
    }

    override fun getContactById(contactId: ContactId): Flow<Contact?> = flow {
        emitAll(
            coreDB.getSphinxDatabaseQueries().contactGetById(contactId)
                .asFlow()
                .mapToOneOrNull(io)
                .map { it?.let { contactDboPresenterMapper.mapFrom(it) } }
                .distinctUntilChanged()
        )
    }

    override fun getContactByPubKey(pubKey: LightningNodePubKey): Flow<Contact?> = flow {
        emitAll(
            coreDB.getSphinxDatabaseQueries().contactGetByPubKey(pubKey)
                .asFlow()
                .mapToOneOrNull(io)
                .map { it?.let { contactDboPresenterMapper.mapFrom(it) } }
                .distinctUntilChanged()
        )
    }

    override suspend fun getAllContactsByIds(contactIds: List<ContactId>): List<Contact> {
        return coreDB.getSphinxDatabaseQueries()
            .contactGetAllByIds(contactIds)
            .executeAsList()
            .map { contactDboPresenterMapper.mapFrom(it) }
    }

    override fun getInviteByContactId(contactId: ContactId): Flow<Invite?> = flow {
        emitAll(
            coreDB.getSphinxDatabaseQueries().inviteGetByContactId(contactId)
                .asFlow()
                .mapToOneOrNull(io)
                .map { it?.let { inviteDboPresenterMapper.mapFrom(it) } }
                .distinctUntilChanged()
        )
    }

    override fun getInviteById(inviteId: InviteId): Flow<Invite?> = flow {
        emitAll(
            coreDB.getSphinxDatabaseQueries().inviteGetById(inviteId)
                .asFlow()
                .mapToOneOrNull(io)
                .map { it?.let { inviteDboPresenterMapper.mapFrom(it) } }
                .distinctUntilChanged()
        )
    }

    override fun getInviteByString(inviteString: InviteString): Flow<Invite?> = flow {
        emitAll(
            coreDB.getSphinxDatabaseQueries().inviteGetByInviteString(inviteString)
                .asFlow()
                .mapToOneOrNull(io)
                .map { it?.let { inviteDboPresenterMapper.mapFrom(it) } }
                .distinctUntilChanged()
        )
    }


    override suspend fun deleteContactById(contactId: ContactId): Response<Any, ResponseError> {
        val queries = coreDB.getSphinxDatabaseQueries()
        val contact = queries.contactGetById(contactId).executeAsOneOrNull()
        val pubKeyToDelete = "c/${contact?.node_pub_key?.value}"

        var owner: Contact? = accountOwner.value

        if (owner == null) {
            try {
                accountOwner.collect {
                    if (it != null) {
                        owner = it
                        throw Exception()
                    }
                }
            } catch (e: Exception) {
            }
            delay(25L)
        }

        if (owner?.id == null || owner!!.id == contactId) {
            val msg = "Account Owner was null, or deleteContactById was called for account owner."
            LOG.w(TAG, msg)
            return Response.Error(ResponseError(msg))
        }

        if (contact != null) {
            //delete all messages
            contact.id.value.toChatId()?.let { chatId ->
                contact.node_pub_key?.value?.let { pubKey -> deleteAllMessagesAndPubKey(pubKey, chatId) }
            }

            contactLock.withLock {
                queries.transaction {
                    deleteContactById(contactId, queries)
                }
            }

            chatLock.withLock {
                queries.transaction {
                    deleteChatById(contactId.value.toChatId(), queries, null)
                }
            }
            connectManager.deleteContact(pubKeyToDelete)
        }

        var deleteContactResponse: Response<Any, ResponseError> = Response.Success(Any())

        return deleteContactResponse
    }

    override suspend fun updateOwner(
        alias: String?,
        privatePhoto: PrivatePhoto?,
        tipAmount: Sat?
    ): Response<Any, ResponseError> {
        val queries = coreDB.getSphinxDatabaseQueries()

        contactLock.withLock {
            queries.contactUpdateOwnerInfo(
                alias?.toContactAlias(),
                privatePhoto ?: PrivatePhoto.False,
                tipAmount
            )
        }

        connectManager.ownerInfoStateFlow.value?.let {
            connectManager.setOwnerInfo(
                it.copy(alias = alias)
            )
        }

        return Response.Success(Any())
    }

    override suspend fun updateContact(
        contactId: ContactId,
        alias: ContactAlias?,
        routeHint: LightningRouteHint?
    ): Response<Any, ResponseError> {
        val queries = coreDB.getSphinxDatabaseQueries()
        var response: Response<Any, ResponseError>? = null

        applicationScope.launch(mainImmediate) {
            try {
                // TODO V2 updateContact

//                networkQueryContact.updateContact(
//                    contactId,
//                    PutContactDto(
//                        alias = alias?.value,
//                        route_hint = routeHint?.value
//                    )
//                ).collect { loadResponse ->
//                    @Exhaustive
//                    when (loadResponse) {
//                        is LoadResponse.Loading -> {
//                        }
//                        is Response.Error -> {
//                            response = loadResponse
//                        }
//                        is Response.Success -> {
//                            contactLock.withLock {
//                                queries.transaction {
//                                    updatedContactIds.add(ContactId(loadResponse.value.id))
//                                    upsertContact(loadResponse.value, queries)
//                                }
//                            }
//                            response = loadResponse
//
//                            LOG.d(TAG, "Contact has been successfully updated")
//                        }
//                    }
//                }
            } catch (e: Exception) {
                LOG.e(TAG, "Failed to update contact", e)

                response = Response.Error(ResponseError(e.message.toString()))
            }
        }.join()

        return response ?: Response.Error(ResponseError("Failed to update contact"))
    }

    override suspend fun updateOwnerDeviceId(deviceId: DeviceId) {
        val queries = coreDB.getSphinxDatabaseQueries()

        try {
            accountOwner.collect { owner ->
                if (owner != null) {
                    if (owner.deviceId != deviceId) {
                        queries.contactUpdateOwnerDeviceId(deviceId)
                    } else {
                        LOG.d(TAG, "DeviceId is up to date")
                        throw Exception()
                    }
                }
            }
        } catch (e: Exception) { }

    }

    @OptIn(RawPasswordAccess::class)
    override suspend fun updateOwnerNameAndKey(
        name: String,
        contactKey: Password
    ): Response<Any, ResponseError> {
        val queries = coreDB.getSphinxDatabaseQueries()
        var response: Response<Any, ResponseError> = Response.Success(Any())

        val publicKey = StringBuilder().let { sb ->
            sb.append(contactKey.value)
            sb.toString()
        }

        try {
            accountOwner.collect { owner ->
                if (owner != null) {
//                    networkQueryContact.updateContact(
//                        owner.id,
//                        PutContactDto(
//                            alias = name,
//                            contact_key = publicKey
//                        )
//                    ).collect { loadResponse ->
//                        @Exhaustive
//                        when (loadResponse) {
//                            is LoadResponse.Loading -> {
//                            }
//                            is Response.Error -> {
//                                response = loadResponse
//                                throw Exception()
//                            }
//                            is Response.Success -> {
//                                contactLock.withLock {
//                                    queries.transaction {
//                                        upsertContact(loadResponse.value, queries)
//                                    }
//                                }
//                                LOG.d(TAG, "Owner name and key has been successfully updated")
//
//                                throw Exception()
//                            }
//                        }
//                    }

                }

            }
        } catch (e: Exception) {
        }

        return response
    }

    override suspend fun updateProfilePic(
        stream: InputStreamProvider,
        mediaType: MediaType,
        fileName: String,
        contentLength: Long?
    ): Response<Any, ResponseError> {
        var response: Response<Any, ResponseError> = Response.Success(true)
        val memeServerHost = MediaHost.DEFAULT

        applicationScope.launch(mainImmediate) {
            try {
                val token = memeServerTokenHandler.retrieveAuthenticationToken(memeServerHost)
                    ?: throw RuntimeException("MemeServerAuthenticationToken retrieval failure")

                val networkResponse = networkQueryMemeServer.uploadAttachment(
                    authenticationToken = token,
                    mediaType = mediaType,
                    stream = stream,
                    fileName = fileName,
                    contentLength = contentLength,
                    memeServerHost = memeServerHost,
                )

                @Exhaustive
                when (networkResponse) {
                    is Response.Error -> {
                        response = networkResponse
                    }
                    is Response.Success -> {
                        val newUrl =
                            PhotoUrl("https://${memeServerHost.value}/public/${networkResponse.value.muid}")

                        // TODO: if chatId method argument is null, update owner record

                        var owner = accountOwner.value

                        if (owner == null) {
                            try {
                                accountOwner.collect { contact ->
                                    if (contact != null) {
                                        owner = contact
                                        throw Exception()
                                    }
                                }
                            } catch (e: Exception) {
                            }
                            delay(25L)
                        }

                        owner?.let { nnOwner ->

                            val queries = coreDB.getSphinxDatabaseQueries()

                            contactLock.withLock {
                                withContext(io) {
                                    queries.contactUpdatePhotoUrl(
                                        newUrl,
                                        nnOwner.id,
                                    )
                                }
                            }

                            connectManager.ownerInfoStateFlow.value?.let {
                                connectManager.setOwnerInfo(
                                    it.copy(picture = newUrl.value)
                                )
                            }
                        } ?: throw IllegalStateException("Failed to retrieve account owner")
                    }
                }
            } catch (e: Exception) {
                response = Response.Error(
                    ResponseError("Failed to update Profile Picture", e)
                )
            }
        }.join()

        return response
    }

    override suspend fun toggleContactBlocked(contact: Contact): Response<Boolean, ResponseError> {
        var response: Response<Boolean, ResponseError> = Response.Success(!contact.isBlocked())

        applicationScope.launch(mainImmediate) {
            val queries = coreDB.getSphinxDatabaseQueries()
            val currentBlockedValue = contact.blocked

            contactLock.withLock {
                withContext(io) {
                    queries.contactUpdateBlocked(
                        if (currentBlockedValue.isTrue()) Blocked.False else Blocked.True,
                        contact.id
                    )
                }
            }

            // TODO V2 toggleBlockedContact

//            networkQueryContact.toggleBlockedContact(
//                contact.id,
//                contact.blocked
//            ).collect { loadResponse ->
//                when (loadResponse) {
//                    is LoadResponse.Loading -> {}
//
//                    is Response.Error -> {
//                        response = loadResponse
//
//                        contactLock.withLock {
//                            withContext(io) {
//                                queries.contactUpdateBlocked(
//                                    currentBlockedValue,
//                                    contact.id
//                                )
//                            }
//                        }
//                    }
//
//                    is Response.Success -> {}
//                }
//            }
        }.join()

        return response
    }

    override suspend fun setGithubPat(
        pat: String
    ): Response<Boolean, ResponseError> {

        var response: Response<Boolean, ResponseError> = Response.Error(
            ResponseError("generate Github PAT failed to execute")
        )

        // TODO v2 generateGithubPAT

        return response
    }

    override suspend fun createOwner(okKey: String, routeHint: String, shortChannelId: String) {
        val queries = coreDB.getSphinxDatabaseQueries()
        val now = DateTime.nowUTC()

        val owner = Contact(
            id = ContactId(0L),
            routeHint = routeHint.toLightningRouteHint(),
            nodePubKey = okKey.toLightningNodePubKey(),
            nodeAlias = null,
            alias = null,
            photoUrl = null,
            privatePhoto = PrivatePhoto.False,
            isOwner = Owner.True,
            status = ContactStatus.AccountOwner,
            rsaPublicKey = null,
            deviceId = null,
            createdAt = now.toDateTime(),
            updatedAt = now.toDateTime(),
            fromGroup = ContactFromGroup.False,
            notificationSound = null,
            tipAmount = null,
            inviteId = null,
            inviteStatus = null,
            blocked = Blocked.False
        )
        contactLock.withLock {
            queries.transaction {
                upsertNewContact(owner, queries)
            }
        }
    }

    override suspend fun createNewContact(contact: NewContact) {
        val queries = coreDB.getSphinxDatabaseQueries()
        val now = DateTime.nowUTC()
        val contactId = getNewContactIndex().firstOrNull()?.value

        val exitingContact = contact.lightningNodePubKey
            ?.let { getContactByPubKey(it).firstOrNull() }

        val status = (contact.confirmed || exitingContact?.status?.isConfirmed() == true)

        if (exitingContact?.nodePubKey != null) {
            val contactStatus = if (status) ContactStatus.Confirmed else ContactStatus.Pending
            val chatStatus = if (status) ChatStatus.Approved else ChatStatus.Pending

            contactLock.withLock {
                withContext(dispatchers.io) {
                    queries.contactUpdateDetails(
                        contact.contactAlias,
                        contact.photoUrl,
                        contactStatus,
                        exitingContact.id
                    )
                }
            }
            chatLock.withLock {
                withContext(dispatchers.io) {
                    queries.chatUpdateDetails(
                        contact.photoUrl,
                        chatStatus,
                        ChatId(exitingContact.id.value)
                    )
                }
            }
        } else {
            val invite = if (contact.invitePrice != null && contact.inviteCode != null) {
                Invite(
                    id = InviteId(contactId ?: -1L),
                    inviteString = InviteString(contact.inviteString ?: "null"),
                    inviteCode = InviteCode(contact.inviteCode ?: ""),
                    paymentRequest = null,
                    contactId = ContactId(contactId ?: -1L),
                    status = InviteStatus.Pending,
                    price = contact.invitePrice,
                    createdAt = now.toDateTime()
                )
            } else {
                null
            }

            val newContact = Contact(
                id = ContactId(exitingContact?.id?.value ?: contactId ?: -1L),
                routeHint = contact.lightningRouteHint,
                nodePubKey = contact.lightningNodePubKey,
                nodeAlias = null,
                alias = exitingContact?.alias ?: contact.contactAlias,
                photoUrl = contact.photoUrl,
                privatePhoto = PrivatePhoto.False,
                isOwner = Owner.False,
                status = if (status) ContactStatus.Confirmed else ContactStatus.Pending,
                rsaPublicKey = null,
                deviceId = null,
                createdAt = contact.createdAt ?: now.toDateTime(),
                updatedAt = contact.createdAt ?: now.toDateTime(),
                fromGroup = ContactFromGroup.False,
                notificationSound = null,
                tipAmount = null,
                inviteId = invite?.id,
                inviteStatus = invite?.status,
                blocked = Blocked.False
            )

            val newChat = Chat(
                id = ChatId(exitingContact?.id?.value ?: contactId ?: -1L),
                uuid = ChatUUID("${UUID.randomUUID()}"),
                name = ChatName(
                    exitingContact?.alias?.value ?: contact.contactAlias?.value ?: "unknown"
                ),
                photoUrl = contact.photoUrl,
                type = ChatType.Conversation,
                status = if (status) ChatStatus.Approved else ChatStatus.Pending,
                contactIds = listOf(ContactId(0), ContactId(contactId ?: -1)),
                isMuted = ChatMuted.False,
                createdAt = contact.createdAt ?: now.toDateTime(),
                groupKey = null,
                host = null,
                pricePerMessage = null,
                escrowAmount = null,
                unlisted = ChatUnlisted.False,
                privateTribe = ChatPrivate.False,
                ownerPubKey = null,
                seen = Seen.False,
                metaData = null,
                myPhotoUrl = null,
                myAlias = null,
                pendingContactIds = emptyList(),
                latestMessageId = null,
                contentSeenAt = null,
                pinedMessage = null,
                notify = NotificationLevel.SeeAll,
                secondBrainUrl = null,
                timezoneEnabled = null,
                timezoneIdentifier = null,
                remoteTimezoneIdentifier = null,
                timezoneUpdated = null
            )

            contactLock.withLock {
                withContext(dispatchers.io) {
                    queries.transaction {
                        upsertNewContact(newContact, queries)
                    }
                }
            }
            chatLock.withLock {
                withContext(dispatchers.io) {
                    queries.transaction {
                        upsertNewChat(
                            newChat,
                            moshi,
                            SynchronizedMap<ChatId, Seen>(),
                            queries,
                            newContact,
                            accountOwner.value?.nodePubKey
                        )
                    }
                }
            }
            inviteLock.withLock {
                invite?.let { invite ->
                    withContext(dispatchers.io) {
                        queries.transaction {
                            upsertNewInvite(invite, queries)
                        }
                    }
                }
            }
        }
    }

    override suspend fun updateOwnerAlias(alias: ContactAlias) {
        val queries = coreDB.getSphinxDatabaseQueries()
        val now = DateTime.nowUTC().toDateTime()

        var owner: Contact? = accountOwner.value

        if (owner == null) {
            try {
                accountOwner.collect { contact ->
                    if (contact != null) {
                        owner = contact
                        throw Exception()
                    }
                }
            } catch (e: Exception) {
            }
            delay(25L)
        }

        val updatedOwner = owner?.copy(
            alias = alias,
            updatedAt = now
        )

        connectManager.ownerInfoStateFlow.value?.let { ownerInfo ->
            connectManager.setOwnerInfo(
                ownerInfo.copy(alias = alias.value)
            )
        }

        if (updatedOwner != null) {
            applicationScope.launch(mainImmediate) {
                contactLock.withLock {
                    queries.transaction {
                        upsertNewContact(updatedOwner, queries)
                    }
                }
            }
        }
    }

    override suspend fun getNewContactIndex(): Flow<ContactId?> = flow {
        emitAll(
            coreDB.getSphinxDatabaseQueries().contactGetLastContactIndex()
                .asFlow()
                .mapToOneOrNull(io)
                .map { dbContactId ->
                    dbContactId?.value?.let {
                        ContactId(it + 1)
                    }
                }
        )
    }


    /////////////////
    /// Lightning ///
    /////////////////
    @Suppress("RemoveExplicitTypeArguments")
    private val accountBalanceStateFlow: MutableStateFlow<NodeBalance?> by lazy {
        MutableStateFlow<NodeBalance?>(null)
    }
    private val balanceLock = Mutex()

    @Suppress("BlockingMethodInNonBlockingContext")
    override suspend fun getAccountBalance(): StateFlow<NodeBalance?> {
        balanceLock.withLock {

            if (accountBalanceStateFlow.value == null) {
                authenticationStorage
                    .getString(REPOSITORY_LIGHTNING_BALANCE, null)
                    ?.let { balanceString ->

                        balanceString.toLong().toNodeBalance()?.let { nodeBalance ->
                            accountBalanceStateFlow.value = nodeBalance
                        }
                    }
            }
        }

        return accountBalanceStateFlow.asStateFlow()
    }

    override val networkRefreshBalance: MutableStateFlow<Long?> by lazy {
        MutableStateFlow(null)
    }

    private val lspLock = Mutex()

    override suspend fun updateLSP(lsp: LightningServiceProvider) {
        val queries = coreDB.getSphinxDatabaseQueries()

        lspLock.withLock {
            queries.transaction {
                updateServerDbo(lsp, queries)
            }
        }
    }

    override suspend fun retrieveLSP(): Flow<LightningServiceProvider> = flow {
        coreDB.getSphinxDatabaseQueries().serverGetAll()
            .asFlow()
            .mapToOneOrNull(io)
            .mapNotNull { dbEntity ->
                dbEntity?.ip?.let { ip ->
                    LightningServiceProvider(ip, dbEntity.pub_key)
                }
            }
            .collect { serviceProvider ->
                emit(serviceProvider)
            }
    }

    ////////////////
    /// Messages ///
    ////////////////
    private val messageLock = Mutex()
    private val messageDboPresenterMapper: MessageDboPresenterMapper by lazy {
        MessageDboPresenterMapper(dispatchers, moshi)
    }

    @OptIn(RawPasswordAccess::class)
    private suspend fun decryptMessageContent(
        messageContent: MessageContent
    ): Response<UnencryptedByteArray, ResponseError> {
        return decryptString(messageContent.value)
    }

    @OptIn(RawPasswordAccess::class)
    private suspend fun decryptMediaKey(
        mediaKey: MediaKey
    ): Response<UnencryptedByteArray, ResponseError> {
        return decryptString(mediaKey.value)
    }

    @OptIn(RawPasswordAccess::class)
    private suspend fun decryptString(
        value: String
    ): Response<UnencryptedByteArray, ResponseError> {
        val privateKey: CharArray = authenticationCoreManager.getEncryptionKey()
            ?.privateKey
            ?.value
            ?: return Response.Error(
                ResponseError("EncryptionKey retrieval failed")
            )

        return rsa.decrypt(
            rsaPrivateKey = RsaPrivateKey(privateKey),
            text = EncryptedString(value),
            dispatcher = default
        )
    }

    @OptIn(UnencryptedDataAccess::class)
    private suspend fun mapMessageDboAndDecryptContentIfNeeded(
        queries: SphinxDatabaseQueries,
        messageDbo: MessageDbo,
        reactions: List<Message>? = null,
        thread: List<Message>? = null,
        purchaseItems: List<Message>? = null,
        replyMessage: ReplyUUID? = null,
        chat: ChatDbo? = null
    ): Message {

        val message: MessageDboWrapper = messageDbo.message_content?.let { messageContent ->

            if (
                messageDbo.type !is MessageType.KeySend &&
                messageDbo.message_content_decrypted == null
            ) {

                val response = decryptMessageContent(messageContent)

                @Exhaustive
                when (response) {
                    is Response.Error -> {
                        messageDboPresenterMapper.mapFrom(messageDbo).let { message ->
                            message._messageDecryptionException = response.exception
                            message._messageDecryptionError = true
                            message
                        }
                    }
                    is Response.Success -> {

                        val message: MessageDboWrapper =
                            messageDboPresenterMapper.mapFrom(messageDbo)

                        response.value
                            .toUnencryptedString(trim = false)
                            .value
                            .toMessageContentDecrypted()
                            ?.let { decryptedContent ->

                                messageLock.withLock {
                                    withContext(io) {
                                        queries.transaction {
                                            queries.messageUpdateContentDecrypted(
                                                decryptedContent,
                                                messageDbo.id
                                            )
                                        }
                                    }
                                }

                                message._messageContentDecrypted = decryptedContent

                            } ?: message.also { it._messageDecryptionError = true }

                        message
                    }
                }

            } else {

                messageDboPresenterMapper.mapFrom(messageDbo)

            }
        } ?: messageDboPresenterMapper.mapFrom(messageDbo)

        if (message.type.canContainMedia) {
            withContext(io) {
                queries.messageMediaGetById(message.id).executeAsOneOrNull()
            }?.let { mediaDbo ->

                mediaDbo.media_key?.let { key ->

                    mediaDbo.media_key_decrypted.let { decrypted ->

                        if (decrypted == null) {
                            val response = decryptMediaKey(MediaKey(key.value))

                            @Exhaustive
                            when (response) {
                                is Response.Error -> {
                                    MessageMediaDboWrapper(mediaDbo).also {
                                        it._mediaKeyDecrypted = null
                                        it._mediaKeyDecryptionError = true
                                        it._mediaKeyDecryptionException = response.exception
                                        message._messageMedia = it
                                    }
                                }
                                is Response.Success -> {

                                    response.value
                                        .toUnencryptedString(trim = false)
                                        .value
                                        .toMediaKeyDecrypted()
                                        .let { decryptedKey ->

                                            message._messageMedia = MessageMediaDboWrapper(mediaDbo)
                                                .also {
                                                    it._mediaKeyDecrypted = decryptedKey

                                                    if (decryptedKey == null) {
                                                        it._mediaKeyDecryptionError = true
                                                    } else {

                                                        messageLock.withLock {

                                                            withContext(io) {
                                                                queries.messageMediaUpdateMediaKeyDecrypted(
                                                                    decryptedKey,
                                                                    mediaDbo.id
                                                                )
                                                            }

                                                        }

                                                    }
                                                }
                                        }
                                }
                            }
                        } else {
                            message._messageMedia = MessageMediaDboWrapper(mediaDbo)
                        }

                    }

                } ?: message.also {
                    it._messageMedia = MessageMediaDboWrapper(mediaDbo)
                }

            } // else do nothing
        }

        if ((thread?.size ?: 0) > 1) {
            message._thread = thread
        }

        message._reactions = reactions
        message._purchaseItems = purchaseItems
        message._isPinned = chat?.pin_message?.value == messageDbo.uuid?.value

        replyMessage?.value?.toMessageUUID()?.let { uuid ->
            queries.messageGetToShowByUUID(uuid).executeAsOneOrNull()?.let { replyDbo ->
                message._replyMessage = mapMessageDboAndDecryptContentIfNeeded(queries, replyDbo)
            }
        }

        message._remoteTimezoneIdentifier = messageDbo.remote_timezone_identifier

        return message
    }

    override fun getAllMessagesToShowByChatId(
        chatId: ChatId,
        limit: Long,
        chatThreadUUID: ThreadUUID?
    ): Flow<List<Message>> =
        flow {
            val queries = coreDB.getSphinxDatabaseQueries()

            emitAll(
                (
                    if (chatThreadUUID != null) {
                        queries.messageGetAllMessagesByThreadUUID(chatId, listOf(chatThreadUUID))
                    } else if (limit > 0) {
                        queries.messageGetAllToShowByChatIdWithLimit(chatId, limit)
                    } else {
                        queries.messageGetAllToShowByChatId(chatId)
                    }
                )
                    .asFlow()
                    .mapToList(io)
                    .map { listMessageDbo ->
                        withContext(default) {

                            val reactionsMap: MutableMap<MessageUUID, ArrayList<Message>> =
                                LinkedHashMap(listMessageDbo.size)

                            val threadMap: MutableMap<MessageUUID, ArrayList<Message>> =
                                LinkedHashMap(listMessageDbo.size)

                            val purchaseItemsMap: MutableMap<MessageMUID, ArrayList<Message>> =
                                LinkedHashMap(listMessageDbo.size)

                            val requestResponsesMap: MutableMap<MessageUUID, Boolean?> =
                                LinkedHashMap(listMessageDbo.size)

                            for (dbo in listMessageDbo) {
                                dbo.uuid?.let { uuid ->
                                    reactionsMap[uuid] = ArrayList(0)
                                }
                                dbo.muid?.let { muid ->
                                    purchaseItemsMap[muid] = ArrayList(0)
                                }
                                dbo.uuid?.let { uuid ->
                                    threadMap[uuid] = ArrayList(0)
                                }
                            }

                            val replyUUIDs = reactionsMap.keys.map { ReplyUUID(it.value) }

                            val threadUUID = threadMap.keys.map { ThreadUUID(it.value) }

                            val purchaseItemsMUIDs =
                                purchaseItemsMap.keys.map { MessageMUID(it.value) }

                            val memberRequestsUUID = listMessageDbo.filter({ it.type.isMemberRequest() && it.uuid != null }).map { ReplyUUID(it.uuid!!.value) }

                            memberRequestsUUID.chunked(500).forEach { chunkedIds ->
                                queries.messageGetAllRequestResponseItemsByReplyUUID(
                                    chatId,
                                    chunkedIds,
                                ).executeAsList()
                                    .let { response ->
                                        response.forEach { dbo ->
                                            dbo.reply_uuid?.let { uuid ->
                                                requestResponsesMap[MessageUUID(uuid.value)] = true
                                            }
                                        }
                                    }
                            }

                            replyUUIDs.chunked(500).forEach { chunkedIds ->
                                queries.messageGetAllReactionsByUUID(
                                    chatId,
                                    chunkedIds,
                                ).executeAsList()
                                    .let { response ->
                                        response.forEach { dbo ->
                                            dbo.reply_uuid?.let { uuid ->
                                                reactionsMap[MessageUUID(uuid.value)]?.add(
                                                    mapMessageDboAndDecryptContentIfNeeded(
                                                        queries,
                                                        dbo
                                                    )
                                                )
                                            }
                                        }
                                    }
                            }

                            threadUUID.chunked(500).forEach { chunkedThreadUUID ->
                                queries.messageGetAllMessagesByThreadUUID(
                                    chatId,
                                    chunkedThreadUUID
                                ).executeAsList()
                                    .let { response ->
                                        response.forEach { dbo ->
                                            dbo.thread_uuid?.let { uuid ->
                                                threadMap[MessageUUID(uuid.value)]?.add(
                                                    mapMessageDboAndDecryptContentIfNeeded(
                                                        queries,
                                                        dbo
                                                    )
                                                )
                                            }
                                        }
                                    }
                            }

                            purchaseItemsMUIDs.chunked(500).forEach { chunkedMUIDs ->
                                queries.messageGetAllPurchaseItemsByMUID(
                                    chatId,
                                    chunkedMUIDs,
                                ).executeAsList()
                                    .let { response ->
                                        response.forEach { dbo ->
                                            dbo.muid?.let { muid ->
                                                purchaseItemsMap[muid]?.add(
                                                    mapMessageDboAndDecryptContentIfNeeded(
                                                        queries,
                                                        dbo
                                                    )
                                                )
                                            }
                                            dbo.original_muid?.let { original_muid ->
                                                purchaseItemsMap[original_muid]?.add(
                                                    mapMessageDboAndDecryptContentIfNeeded(
                                                        queries,
                                                        dbo
                                                    )
                                                )
                                            }
                                        }
                                    }
                            }

                            val chatDbo = queries.chatGetById(chatId).executeAsOneOrNull()
                            var isMyTribe = false

                            chatDbo?.let {
                                val chat = chatDboPresenterMapper.mapFrom(chatDbo)
                                isMyTribe = chat.isTribeOwnedByAccount(accountOwner.value?.nodePubKey)
                            }

                            val filteredMemberRequests = listMessageDbo.filter { dbo ->
                                if (dbo.type.isGroupKick() && isMyTribe) {
                                    false
                                } else if (!dbo.type.isMemberRequest()) {
                                    true
                                } else {
                                    val hasResponse = dbo.uuid?.let { uuid ->
                                        requestResponsesMap[MessageUUID(uuid.value)] == true
                                    } ?: false

                                    !hasResponse
                                }
                            }

                            filteredMemberRequests.reversed().map { dbo ->
                                mapMessageDboAndDecryptContentIfNeeded(
                                    queries,
                                    dbo,
                                    dbo.uuid?.let { reactionsMap[it] },
                                    dbo.uuid?.let { threadMap[it] },
                                    dbo.muid?.let { purchaseItemsMap[it] },
                                    dbo.reply_uuid,
                                    chatDbo
                                )
                            }
                        }
                    }
            )
        }

    override fun getMessageById(messageId: MessageId): Flow<Message?> = flow {
        val queries = coreDB.getSphinxDatabaseQueries()
        emitAll(getMessageByIdImpl(messageId, queries))
    }

    override fun getSecondBrainTribes(): Flow<List<Chat?>> = flow {
        emitAll(
            coreDB.getSphinxDatabaseQueries()
                .chatGetSecondBrainTribes()
                .asFlow()
                .mapToList(io)
                .map { listChatDbo ->
                    listChatDbo.map {
                        chatDboPresenterMapper.mapFrom(it)
                    }
                }
                .distinctUntilChanged()
        )
    }

    override fun getMessagesByIds(messagesIds: List<MessageId>): Flow<List<Message?>> = flow {
        emitAll(
            coreDB.getSphinxDatabaseQueries()
                .messageGetMessagesByIds(messagesIds)
                .asFlow()
                .mapToList(io)
                .map { listMessageDbo ->
                    listMessageDbo.map {
                        messageDboPresenterMapper.mapFrom(it)
                    }
                }
                .distinctUntilChanged()
        )
    }

    override fun messageGetOkKeysByChatId(chatId: ChatId): Flow<List<MessageId>> = flow {
        emitAll(
            coreDB.getSphinxDatabaseQueries()
                .messageGetOkKeysByChatId(chatId)
                .asFlow()
                .mapToList(io)
                .distinctUntilChanged()
        )
    }

    override fun getSentConfirmedMessagesByChatId(chatId: ChatId): Flow<List<Message>> = flow {
        emitAll(
            coreDB.getSphinxDatabaseQueries()
                .messageGetSentConfirmedMessages(chatId)
                .asFlow()
                .mapToList(io)
                .map { listMessageDbo ->
                    listMessageDbo.map {
                        messageDboPresenterMapper.mapFrom(it)
                    }
                }
                .distinctUntilChanged()
        )
    }

    override fun getDeletedMessages(): Flow<List<Message>> = flow {
        emitAll(
            coreDB.getSphinxDatabaseQueries()
                .messageGetDeletedMessages()
                .asFlow()
                .mapToList(io)
                .map { listMessageDbo ->
                    listMessageDbo.map {
                        messageDboPresenterMapper.mapFrom(it)
                    }
                }
                .distinctUntilChanged()
        )
    }

    override fun getMessagesByPaymentHashes(paymentHashes: List<LightningPaymentHash>): Flow<List<Message?>> = flow {
        emitAll(
            coreDB.getSphinxDatabaseQueries()
                .messageGetByPaymentHashes(paymentHashes)
                .asFlow()
                .mapToList(io)
                .map { listMessageDbo ->
                    listMessageDbo.map {
                        messageDboPresenterMapper.mapFrom(it)
                    }
                }
                .distinctUntilChanged()
        )
    }

    override fun getMaxIdMessage(): Flow<Long?> = flow {
        emitAll(
            coreDB.getSphinxDatabaseQueries()
                .messageGetMaxId()
                .asFlow()
                .mapToOneOrNull(io)
                .map { it?.MAX }
        )
    }

    override fun getLastMessage(): Flow<Message?> = flow {
        val queries = coreDB.getSphinxDatabaseQueries()
        emitAll(
            queries.messageGetLastMessage()
                .asFlow()
                .mapToOneOrNull(io)
                .map {
                    it?.let { messageDbo ->
                        mapMessageDboAndDecryptContentIfNeeded(queries, messageDbo)
                    }
                }
                .distinctUntilChanged()
        )
    }

    override fun getThreadUUIDMessagesByChatId(chatId: ChatId): Flow<List<Message>> = flow {
        emitAll(
            coreDB.getSphinxDatabaseQueries()
                .messagesGetAllThreadUUIDByChatId(chatId, ::MessageDbo)
                .asFlow()
                .mapToList(io)
                .map { listMessageDbo ->
                    listMessageDbo.map {
                        messageDboPresenterMapper.mapFrom(it)
                    }
                }
                .distinctUntilChanged()
        )
    }

    override fun getThreadUUIDMessagesByUUID(
        chatId: ChatId,
        threadUUID: ThreadUUID
    ): Flow<List<Message>> = flow {
        emitAll(
            coreDB.getSphinxDatabaseQueries()
                .messageGetAllMessagesByThreadUUID(chatId, listOf(threadUUID))
                .asFlow()
                .mapToList(io)
                .map { listMessageDbo ->
                    listMessageDbo.map {
                        messageDboPresenterMapper.mapFrom(it)
                    }
                }
                .distinctUntilChanged()
        )
    }

    private fun getMessageByIdImpl(
        messageId: MessageId,
        queries: SphinxDatabaseQueries
    ): Flow<Message?> =
        queries.messageGetById(messageId)
            .asFlow()
            .mapToOneOrNull(io)
            .map {
                it?.let { messageDbo ->
                    mapMessageDboAndDecryptContentIfNeeded(queries, messageDbo)
                }
            }
            .distinctUntilChanged()

    override fun searchMessagesBy(chatId: ChatId, term: String): Flow<List<Message>> = flow {
        emitAll(
            coreDB.getSphinxDatabaseQueries()
                .messagesSearchByTerm(
                    chatId,
                    "%${term.lowercase()}%"
                )
                .asFlow()
                .mapToList(io)
                .map { listMessageDbo ->
                    listMessageDbo.map {
                        messageDboPresenterMapper.mapFrom(it)
                    }
                }
                .distinctUntilChanged()
        )
    }

    override fun getTribeLastMemberRequestBySenderAlias(
        alias: SenderAlias,
        chatId: ChatId,
    ): Flow<Message?> = flow {
        val queries = coreDB.getSphinxDatabaseQueries()

        emitAll(
            queries.messageLastMemberRequestGetBySenderAlias(alias, chatId)
                .asFlow()
                .mapToOneOrNull(io)
                .map {
                    it?.let { messageDbo ->
                        mapMessageDboAndDecryptContentIfNeeded(queries, messageDbo)
                    }
                }
                .distinctUntilChanged()
        )
    }

    override fun getMessageByUUID(messageUUID: MessageUUID): Flow<Message?> = flow {
        val queries = coreDB.getSphinxDatabaseQueries()
        emitAll(
            queries.messageGetByUUID(messageUUID)
                .asFlow()
                .mapToOneOrNull(io)
                .map {
                    it?.let { messageDbo ->
                        mapMessageDboAndDecryptContentIfNeeded(queries, messageDbo)
                    }
                }
                .distinctUntilChanged()
        )
    }

    override suspend fun getAllMessagesByUUID(messageUUIDs: List<MessageUUID>): List<Message> {
        val queries = coreDB.getSphinxDatabaseQueries()

        return queries.messageGetAllByUUID(messageUUIDs)
            .executeAsList()
            .map { mapMessageDboAndDecryptContentIfNeeded(queries, it) }
    }

    override suspend fun fetchPinnedMessageByUUID(
        messageUUID: MessageUUID,
        chatId: ChatId
    ) {}

    override fun updateMessageContentDecrypted(
        messageId: MessageId,
        messageContentDecrypted: MessageContentDecrypted
    ) {
        applicationScope.launch(io) {
            val queries = coreDB.getSphinxDatabaseQueries()
            messageLock.withLock {
                withContext(io) {
                    queries.transaction {
                        queries.messageUpdateContentDecrypted(
                            messageContentDecrypted,
                            messageId
                        )
                    }
                }
            }
        }
    }

    @Suppress("RemoveExplicitTypeArguments")
    private val chatSeenMap: SynchronizedMap<ChatId, Seen> by lazy {
        SynchronizedMap<ChatId, Seen>()
    }

    override suspend fun readMessages(chatId: ChatId) {
        readMessagesImpl(
            chatId = chatId,
            queries = coreDB.getSphinxDatabaseQueries(),
            executeNetworkRequest = true
        )
    }

    private suspend fun readMessagesImpl(
        chatId: ChatId,
        queries: SphinxDatabaseQueries,
        executeNetworkRequest: Boolean
    ) {
        messageLock.withLock {
            withContext(io) {
                queries.transaction {
                    queries.updateSeen(chatId)
                }
            }
        }

        val message = queries.messageGetMaxIdByChatId(chatId).executeAsOneOrNull()
        val contact = queries.contactGetById(ContactId(chatId.value)).executeAsOneOrNull()
        val chat = queries.chatGetById(chatId).executeAsOneOrNull()

        if (message != null) {
            if (contact != null) {
                contact.node_pub_key?.value?.let { pubKey ->
                    connectManager.setReadMessage(pubKey, message.id.value)
                }
            } else {
                chat?.uuid?.value?.let { pubKey ->
                    connectManager.setReadMessage(pubKey, message.id.value)
                }
            }
        }
    }

    private val provisionalMessageLock = Mutex()

    private fun messageText(sendMessage: SendMessage, moshi: Moshi): String? {
        try {
            if (sendMessage.giphyData != null) {
                return sendMessage.giphyData?.let {
                    "${GiphyData.MESSAGE_PREFIX}${it.toJson(moshi).toByteArray().encodeBase64()}"
                }
            }
        } catch (e: Exception) {
            LOG.e(TAG, "GiphyData toJson failed: ", e)
        }

        try {
            if (sendMessage.podcastClip != null) {
                return sendMessage.podcastClip?.let {
                    "${PodcastClip.MESSAGE_PREFIX}${it.toJson(moshi)}"
                }
            }
        } catch (e: Exception) {
            LOG.e(TAG, "PodcastClip toJson failed: ", e)
        }

        return sendMessage.text
    }

    // TODO: Rework to handle different message types
    @OptIn(RawPasswordAccess::class)
    override fun sendMessage(sendMessage: SendMessage?) {
        if (sendMessage == null) return

        applicationScope.launch(mainImmediate) {

            val queries = coreDB.getSphinxDatabaseQueries()

            // TODO: Update SendMessage to accept a Chat && Contact instead of just IDs
            val chat: Chat? = sendMessage.chatId?.let {
                getChatById(it).firstOrNull()
            }

            val contact: Contact? = sendMessage.contactId?.let {
                getContactById(it).firstOrNull()
            }

            val owner: Contact? = accountOwner.value
                ?: let {
                    // TODO: Handle this better...
                    var owner: Contact? = null
                    try {
                        accountOwner.collect {
                            if (it != null) {
                                owner = it
                                throw Exception()
                            }
                        }
                    } catch (e: Exception) {
                    }
                    delay(25L)
                    owner
                }

            val ownerPubKey = owner?.nodePubKey

            if (owner == null) {
                LOG.w(TAG, "Owner returned null")
                return@launch
            }

            if (ownerPubKey == null) {
                LOG.w(TAG, "Owner's public key was null")
                return@launch
            }

            val message = messageText(sendMessage, moshi)
            val isPaidMessage: Boolean = (sendMessage.paidMessagePrice?.value ?: 0) > 0
            val media: AttachmentInfo? = sendMessage.attachmentInfo

//            if (message == null && media == null && !sendMessage.isTribePayment) {
//                return@launch
//            }

            val pricePerMessage = chat?.pricePerMessage?.value ?: 0
            val escrowAmount = chat?.escrowAmount?.value ?: 0
//            val priceToMeet = sendMessage.priceToMeet?.value ?: 0
            val messagePrice = (pricePerMessage + escrowAmount).toSat() ?: Sat(0)

            val messageType = when {
                (media != null) -> {
                    MessageType.Attachment
                }
                (sendMessage.groupAction != null) -> {
                    sendMessage.groupAction!!
                }
                (sendMessage.isBoost) -> {
                    MessageType.Boost
                }
                (sendMessage.isCall) -> {
                    MessageType.CallLink
                }
                (sendMessage.isTribePayment) -> {
                    MessageType.DirectPayment
                }
                else -> {
                    MessageType.Message
                }
            }

//            //If is tribe payment, reply UUID is sent to identify recipient. But it's not a response
            val replyUUID = when {
                (sendMessage.isTribePayment) -> {
                    null
                }
                else -> {
                    sendMessage.replyUUID
                }
            }

            val threadUUID = sendMessage.threadUUID

            val metadata: String? = if(
                chat?.timezoneUpdated?.isTrue() == true &&
                chat.timezoneEnabled?.isTrue() == true
            ) {
                val timezoneAbbreviation = DateTime.getTimezoneAbbreviationFrom(chat.timezoneIdentifier?.value)
                MessageMetadata(tz = timezoneAbbreviation).toJson(moshi)
            } else {
                null
            }

            if (metadata != null && chat?.id != null) {
                updateTimezoneFlag(
                    timezoneUpdated = TimezoneUpdated.False,
                    chatId = chat.id
                )
            }

            val provisionalMessageId: MessageId? = chat?.let { chatDbo ->
                // Build provisional message and insert
                provisionalMessageLock.withLock {

                    val currentProvisionalId: MessageId? = withContext(io) {
                        queries.messageGetLowestProvisionalMessageId().executeAsOneOrNull()
                    }
                    val provisionalId = MessageId((currentProvisionalId?.value ?: 0L) - 1)

                    withContext(io) {
                        queries.transaction {

                            // The following parms are set to null to make the upsert to work
                            // type, message_content, message_decrypted, status

                            queries.messageUpsert(
                                MessageStatus.Pending,
                                Seen.True,
                                sendMessage.senderAlias ?: chatDbo.myAlias?.value?.toSenderAlias(),
                                chatDbo.myPhotoUrl,
                                null,
                                replyUUID,
                                messageType,
                                null,
                                null,
                                Push.False,
                                null,
                                threadUUID,
                                null,
                                null,
                                provisionalId,
                                null,
                                chatDbo.id,
                                owner.id,
                                sendMessage.contactId,
                                sendMessage.tribePaymentAmount ?: sendMessage.paidMessagePrice ?: messagePrice ,
                                null,
                                null,
                                DateTime.nowUTC().toDateTime(),
                                null,
                                null,
                                message?.toMessageContentDecrypted() ?: sendMessage.text?.toMessageContentDecrypted(),
                                null,
                                false.toFlagged(),
                                if (chat.timezoneEnabled?.isTrue() == true) chat.timezoneIdentifier?.value?.toRemoteTimezoneIdentifier() else null
                            )
                        }
                        provisionalId
                    }
                }
            }

            if (contact != null || chat != null) {
                if (media != null) {
                    val password = PasswordGenerator(MEDIA_KEY_SIZE).password
                    val token = memeServerTokenHandler.retrieveAuthenticationToken(MediaHost.DEFAULT)
                            ?: provisionalMessageId?.let { provId ->
                                withContext(io) {
                                    queries.messageUpdateStatus(MessageStatus.Failed, provId)
                                }
                                return@launch
                            } ?: return@launch

                    val response = networkQueryMemeServer.uploadAttachmentEncrypted(
                        token,
                        media.mediaType,
                        media.file,
                        media.fileName,
                        password,
                        MediaHost.DEFAULT,
                    )

                    @Exhaustive
                    when (response) {
                        is Response.Error -> {
                            LOG.e(TAG, response.message, response.exception)

                            provisionalMessageId?.let { provId ->
                                withContext(io) {
                                    queries.messageUpdateStatus(MessageStatus.Failed, provId)
                                }
                            }
                            return@launch
                        }

                        is Response.Success -> {
                            val pubKey = contact?.nodePubKey?.value ?: chat?.uuid?.value

                            pubKey?.let { nnPubKey ->

                                val amount = sendMessage.paidMessagePrice?.value

                                val mediaTokenValue = connectManager.generateMediaToken(
                                    nnPubKey,
                                    response.value.muid,
                                    MediaHost.DEFAULT.value,
                                    null,
                                    amount,
                                )

                                val mediaKey = MediaKey(password.value.copyOf().joinToString(""))

                                queries.messageMediaUpsert(
                                    mediaKey,
                                    media.mediaType,
                                    mediaTokenValue?.toMediaToken() ?: MediaToken.PROVISIONAL_TOKEN,
                                    provisionalMessageId ?: MessageId(Long.MIN_VALUE),
                                    chat?.id ?: ChatId(ChatId.NULL_CHAT_ID.toLong()),
                                    MediaKeyDecrypted(password.value.copyOf().joinToString("")),
                                    media.file,
                                    sendMessage.attachmentInfo?.fileName
                                )

                                sendNewMessage(
                                    contact?.nodePubKey?.value ?: chat?.uuid?.value ?: "",
                                    message ?: sendMessage.text ?: "",
                                    media,
                                    mediaTokenValue?.toMediaToken(),
                                    if (isPaidMessage && chat?.isTribe() == false) null else mediaKey,
                                    messageType,
                                    provisionalMessageId,
                                    sendMessage.tribePaymentAmount ?: messagePrice,
                                    replyUUID,
                                    threadUUID,
                                    chat?.isTribe() ?: false,
                                    sendMessage.memberPubKey,
                                    metadata
                                )

                                LOG.d("MQTT_MESSAGES", "Media Message was sent. mediatoken=$mediaTokenValue mediakey$mediaKey" )
                            }
                        }
                    }
                } else {
                    sendNewMessage(
                        contact?.nodePubKey?.value ?: chat?.uuid?.value ?: "",
                        message ?: sendMessage.text ?: "",
                        null,
                        null,
                        null,
                        messageType,
                        provisionalMessageId,
                        sendMessage.tribePaymentAmount ?: messagePrice,
                        replyUUID,
                        threadUUID,
                        chat?.isTribe() ?: false,
                        sendMessage.memberPubKey,
                        metadata
                    )
                }
            }
        }
    }

    private suspend fun getRemoteTextMap(
        unencryptedString: UnencryptedString?,
        contact: Contact?,
        chat: Chat?,
    ): Map<String, String>? {

        return if (unencryptedString != null) {
            contact?.id?.let { nnContactId ->
                // we know it's a conversation as the contactId is always sent
                contact.rsaPublicKey?.let { pubKey ->

                    val response = rsa.encrypt(
                        pubKey,
                        unencryptedString,
                        formatOutput = false,
                        dispatcher = default,
                    )

                    @Exhaustive
                    when (response) {
                        is Response.Error -> {
                            LOG.e(TAG, response.message, response.exception)
                            null
                        }
                        is Response.Success -> {
                            mapOf(Pair(nnContactId.value.toString(), response.value.value))
                        }
                    }
                }

            } ?: chat?.groupKey?.value?.let { rsaPubKeyString ->
                val response = rsa.encrypt(
                    RsaPublicKey(rsaPubKeyString.toCharArray()),
                    unencryptedString,
                    formatOutput = false,
                    dispatcher = default,
                )

                @Exhaustive
                when (response) {
                    is Response.Error -> {
                        LOG.e(TAG, response.message, response.exception)
                        null
                    }
                    is Response.Success -> {
                        mapOf(Pair("chat", response.value.value))
                    }
                }
            }
        } else {
            null
        }
    }

    private suspend fun getMediaKeyMap(
        ownerId: ContactId,
        mediaKey: MediaKey,
        unencryptedMediaKey: UnencryptedString?,
        contact: Contact?,
        chat: Chat?,
    ): Map<String, String>? {

        return if (unencryptedMediaKey != null) {
            val map: MutableMap<String, String> = LinkedHashMap(2)

            map[ownerId.value.toString()] = mediaKey.value

            contact?.id?.let { nnContactId ->
                // we know it's a conversation as the contactId is always sent
                contact.rsaPublicKey?.let { pubKey ->

                    val response = rsa.encrypt(
                        pubKey,
                        unencryptedMediaKey,
                        formatOutput = false,
                        dispatcher = default,
                    )

                    @Exhaustive
                    when (response) {
                        is Response.Error -> {
                            LOG.e(TAG, response.message, response.exception)
                        }
                        is Response.Success -> {
                            map[nnContactId.value.toString()] = response.value.value
                        }
                    }
                }

            } ?: chat?.groupKey?.value?.let { rsaPubKeyString ->
                val response = rsa.encrypt(
                    RsaPublicKey(rsaPubKeyString.toCharArray()),
                    unencryptedMediaKey,
                    formatOutput = false,
                    dispatcher = default,
                )

                @Exhaustive
                when (response) {
                    is Response.Error -> {
                        LOG.e(TAG, response.message, response.exception)
                    }
                    is Response.Success -> {
                        map["chat"] = response.value.value
                    }
                }
            }

            map
        } else {
            null
        }
    }

    @OptIn(RawPasswordAccess::class)
    suspend fun sendMessage(
        provisionalMessageId: MessageId?,
        messageContentDecrypted: MessageContentDecrypted?,
        media: Triple<Password, MediaKey, AttachmentInfo>?,
    ) {
        val queries = coreDB.getSphinxDatabaseQueries()

//        networkQueryMessage.sendMessage(postMessageDto).collect { loadResponse ->
//            @Exhaustive
//            when (loadResponse) {
//                is LoadResponse.Loading -> {
//                }
//                is Response.Error -> {
//                    LOG.e(TAG, loadResponse.message, loadResponse.exception)
//
//                    messageLock.withLock {
//                        provisionalMessageId?.let { provId ->
//                            withContext(io) {
//                                queries.messageUpdateStatus(MessageStatus.Failed, provId)
//                            }
//                        }
//                    }
//
//                }
//                is Response.Success -> {
//
//                    loadResponse.value.apply {
//                        if (media != null) {
//                            setMediaKeyDecrypted(media.first.value.joinToString(""))
//                            setMediaLocalFile(media.third.file)
//                        }
//
//                        if (messageContentDecrypted != null) {
//                            setMessageContentDecrypted(messageContentDecrypted.value)
//                        }
//                    }
//
//                    chatLock.withLock {
//                        messageLock.withLock {
//                            contactLock.withLock {
//                                withContext(io) {
//                                    queries.transaction {
//                                        // chat is returned only if this is the
//                                        // first message sent to a new contact
//                                        loadResponse.value.chat?.let { chatDto ->
//                                            upsertChat(
//                                                chatDto,
//                                                moshi,
//                                                chatSeenMap,
//                                                queries,
//                                                loadResponse.value.contact,
//                                                accountOwner.value?.nodePubKey
//                                            )
//                                        }
//
//                                        loadResponse.value.contact?.let { contactDto ->
//                                            upsertContact(contactDto, queries)
//                                        }
//
//                                        upsertMessage(
//                                            loadResponse.value,
//                                            queries,
//                                            media?.third?.fileName
//                                        )
//
//                                        provisionalMessageId?.let { provId ->
//                                            deleteMessageById(provId, queries)
//                                        }
//                                    }
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//        }
    }

    override fun resendMessage(message: Message, chat: Chat) {
        applicationScope.launch(mainImmediate) {
            val queries = coreDB.getSphinxDatabaseQueries()

            val pricePerMessage = chat.pricePerMessage?.value ?: 0
            val escrowAmount = chat.escrowAmount?.value ?: 0
            val messagePrice = (pricePerMessage + escrowAmount).toSat() ?: Sat(0)

            val contact: Contact? = if (chat.type.isConversation()) {
                chat.contactIds.elementAtOrNull(1)?.let { contactId ->
                    getContactById(contactId).firstOrNull()
                }
            } else {
                null
            }

            val remoteTextMap: Map<String, String>? = getRemoteTextMap(
                UnencryptedString(message.messageContentDecrypted?.value ?: ""),
                contact,
                chat
            )

            sendMessage(
                message.id,
                message.messageContentDecrypted,
                null
            )
        }
    }

//    override fun flagMessage(message: Message, chat: Chat) {
//        applicationScope.launch(mainImmediate) {
//            val queries = coreDB.getSphinxDatabaseQueries()
//
//            messageLock.withLock {
//                withContext(io) {
//                    queries.messageUpdateFlagged(
//                        true.toFlagged(),
//                        message.id
//                    )
//                }
//            }
//
//            getContactByPubKey(supportContactPubKey).firstOrNull()?.let { supportContact ->
//                val messageSender = getContactById(message.sender).firstOrNull()
//
//                var flagMessageContent =
//                    "Message Flagged\n- Message: ${message.uuid?.value ?: "Empty Message UUID"}\n- Sender: ${messageSender?.nodePubKey?.value ?: "Empty Sender"}"
//
//                if (chat.isTribe()) {
//                    flagMessageContent += "\n- Tribe: ${chat.uuid.value}"
//                }
//
//                val messageBuilder = SendMessage.Builder()
//                messageBuilder.setText(flagMessageContent.trimIndent())
//
//                messageBuilder.setContactId(supportContact.id)
//
//                getConversationByContactId(supportContact.id).firstOrNull()
//                    ?.let { supportContactChat ->
//                        messageBuilder.setChatId(supportContactChat.id)
//                    }
//
//                sendMessage(
//                    messageBuilder.build().first
//                )
//            }
//        }
//    }

    override suspend fun deleteMessage(message: Message) {
        val queries = coreDB.getSphinxDatabaseQueries()
        val contact = getContactById(ContactId(message.chatId.value)).firstOrNull()
        val chatTribe = getChatById(message.chatId).firstOrNull()

        if (message.id.isProvisionalMessage) {
            messageLock.withLock {
                withContext(io) {
                    queries.transaction {
                        deleteMessageById(message.id, queries)
                    }
                }
            }
        } else {

            messageLock.withLock {
                withContext(io) {
                    queries.messageUpdateStatus(MessageStatus.Deleted, message.id)
                }
            }

            val newMessage = chat.sphinx.example.wrapper_mqtt.Message(
                "",
                null,
                null,
                null,
                null,
                message.uuid?.value,
                null,
                null,
                null,
                null
            ).toJson(moshi)

            val contactPubKey = contact?.nodePubKey?.value ?: chatTribe?.uuid?.value
            val isTribe = (chatTribe != null)

            if (contactPubKey != null) {
                connectManager.deleteMessage(
                    newMessage,
                    contactPubKey,
                    isTribe
                )
            }
        }
    }

    override suspend fun deleteAllMessagesAndPubKey(pubKey: String, chatId: ChatId) {
        val messagesIds = messageGetOkKeysByChatId(chatId).firstOrNull()
        if (messagesIds != null) {
            connectManager.deleteContactMessages(messagesIds.map { it.value })
            connectManager.deletePubKeyMessages(pubKey)
        }
    }

    override suspend fun sendPayment(
        sendPayment: SendPayment?
    ): Response<Any, ResponseError> {

        var response: Response<Any, ResponseError> = Response.Success(true)

        if (sendPayment == null) {
            response = Response.Error(
                ResponseError("Payment params cannot be null")
            )
            return response
        }

        applicationScope.launch(mainImmediate) {
            val queries = coreDB.getSphinxDatabaseQueries()

            val contact: ContactDbo? = sendPayment.contactId?.let {
                withContext(io) {
                    queries.contactGetById(it).executeAsOneOrNull()
                }
            }

            val owner: Contact? = accountOwner.value
                ?: let {
                    // TODO: Handle this better...
                    var owner: Contact? = null
                    try {
                        accountOwner.collect {
                            if (it != null) {
                                owner = it
                                throw Exception()
                            }
                        }
                    } catch (e: Exception) {
                    }
                    delay(25L)
                    owner
                }

            if (owner == null) {
                response = Response.Error(
                    ResponseError("Owner cannot be null")
                )
                return@launch
            }

            val text = sendPayment.text

            val currentProvisionalId: MessageId? = withContext(io) {
                queries.messageGetLowestProvisionalMessageId().executeAsOneOrNull()
            }
            val provisionalId = MessageId((currentProvisionalId?.value ?: 0L) - 1)

            val newPayment = NewMessage(
                id = provisionalId,
                uuid = null,
                chatId = sendPayment.chatId ?: ChatId(ChatId.NULL_CHAT_ID.toLong()),
                type = MessageType.DirectPayment,
                sender = owner.id,
                receiver = null,
                amount = Sat(sendPayment.amount),
                date = DateTime.nowUTC().toDateTime(),
                expirationDate = null,
                messageContent = null,
                status = MessageStatus.Pending,
                seen = Seen.True,
                senderAlias = null,
                senderPic = null,
                originalMUID = sendPayment.paymentTemplate?.muid?.toMessageMUID(),
                replyUUID = null,
                flagged = false.toFlagged(),
                recipientAlias = null,
                recipientPic = null,
                person = null,
                threadUUID = null,
                errorMessage = null,
                isPinned = false,
                messageContentDecrypted = text?.toMessageContentDecrypted(),
                messageDecryptionError = false,
                messageDecryptionException = null,
                messageMedia = null,
                feedBoost = null,
                callLinkMessage = null,
                podcastClip = null,
                giphyData = null,
                reactions = null,
                purchaseItems = null,
                replyMessage = null,
                thread = null
            )

            var mediaTokenValue: String? = null

            sendPayment.paymentTemplate?.let { template ->

                mediaTokenValue = connectManager.generateMediaToken(
                    contact?.node_pub_key?.value ?: "",
                    sendPayment.paymentTemplate?.muid ?: "",
                    MediaHost.DEFAULT.value,
                    sendPayment.paymentTemplate?.getDimensions(),
                    null
                )

                queries.messageMediaUpsert(
                    null,
                    MediaType.IMAGE.toMediaType(),
                   mediaTokenValue?.toMediaToken() ?: MediaToken.PROVISIONAL_TOKEN,
                    provisionalId,
                    ChatId(contact?.id?.value ?: ChatId.NULL_CHAT_ID.toLong()),
                    null,
                    null,
                    null
                )

            }

            val newPaymentMessage = chat.sphinx.example.wrapper_mqtt.Message(
                text,
                null,
                mediaTokenValue,
                null,
                MediaType.IMAGE,
                null,
                null,
                null,
                null,
                null
            ).toJson(moshi)


            chatLock.withLock {
                messageLock.withLock {
                    withContext(io) {

                        queries.transaction {
                            upsertNewMessage(newPayment, queries, null)
                        }
                    }
                }
            }

            contact?.let { nnContact ->
                connectManager.sendMessage(
                    newPaymentMessage,
                    contact.node_pub_key?.value ?: "",
                    provisionalId.value,
                    MessageType.DIRECT_PAYMENT,
                    sendPayment.amount,
                )
            }

        }.join()

        return response
    }

    override suspend fun sendTribePayment(
        chatId: ChatId,
        amount: Sat,
        messageUUID: MessageUUID,
        text: String,
    ) {
        applicationScope.launch(mainImmediate) {

            val sendMessageBuilder = SendMessage.Builder()
            sendMessageBuilder.setChatId(chatId)
            sendMessageBuilder.setTribePaymentAmount(amount)
            sendMessageBuilder.setText(text)
            sendMessageBuilder.setReplyUUID(messageUUID.value.toReplyUUID())
            sendMessageBuilder.setIsTribePayment(true)

            sendMessage(
                sendMessageBuilder.build().first
            )
        }
    }

    override suspend fun boostMessage(
        chatId: ChatId,
        pricePerMessage: Sat,
        escrowAmount: Sat,
        messageUUID: MessageUUID,
    ): Response<Any, ResponseError> {

        var response: Response<Any, ResponseError> = Response.Success(true)

        applicationScope.launch(mainImmediate) {
            val queries = coreDB.getSphinxDatabaseQueries()
            val contact = getContactById(ContactId(chatId.value)).firstOrNull()
            val currentChat = getChatById(chatId).firstOrNull()

            val owner: Contact = accountOwner.value.let {
                if (it != null) {
                    it
                } else {
                    var owner: Contact? = null
                    val retrieveOwnerJob = applicationScope.launch(mainImmediate) {
                        try {
                            accountOwner.collect { contact ->
                                if (contact != null) {
                                    owner = contact
                                    throw Exception()
                                }
                            }
                        } catch (e: Exception) {
                        }
                        delay(20L)
                    }

                    delay(200L)
                    retrieveOwnerJob.cancelAndJoin()

                    owner ?: let {
                        response = Response.Error(
                            ResponseError("Owner Contact returned null")
                        )
                        return@launch
                    }
                }
            }

            val currentProvisionalId: MessageId? = withContext(io) {
                queries.messageGetLowestProvisionalMessageId().executeAsOneOrNull()
            }
            val provisionalId = MessageId((currentProvisionalId?.value ?: 0L) - 1)

            val newBoost = NewMessage(
                id = provisionalId,
                uuid = null,
                chatId = chatId,
                type = MessageType.Boost,
                sender = owner.id,
                receiver = null,
                amount = owner.tipAmount ?: Sat(20L),
                date = DateTime.nowUTC().toDateTime(),
                expirationDate = null,
                messageContent = null,
                status = MessageStatus.Confirmed,
                seen = Seen.True,
                senderAlias = null,
                senderPic = null,
                originalMUID = null,
                replyUUID = ReplyUUID(messageUUID.value),
                flagged = false.toFlagged(),
                recipientAlias = null,
                recipientPic = null,
                person = null,
                threadUUID = null,
                errorMessage = null,
                isPinned = false,
                messageContentDecrypted = null,
                messageDecryptionError = false,
                messageDecryptionException = null,
                messageMedia = null,
                feedBoost = null,
                callLinkMessage = null,
                podcastClip = null,
                giphyData = null,
                reactions = null,
                purchaseItems = null,
                replyMessage = null,
                thread = null
            )

            val newBoostMessage = chat.sphinx.example.wrapper_mqtt.Message(
                null,
                null,
                null,
                null,
                null,
                messageUUID.value,
                null,
                null,
                null,
                null
            ).toJson(moshi)

            chatLock.withLock {
                messageLock.withLock {
                    withContext(io) {

                        queries.transaction {
                            upsertNewMessage(newBoost, queries, null)
                        }
                    }

                    queries.transaction {
                        updateChatNewLatestMessage(
                            newBoost,
                            chatId,
                            latestMessageUpdatedTimeMap,
                            queries
                        )
                    }
                }
            }

            val contactPubKey = contact?.nodePubKey?.value ?: currentChat?.uuid?.value

            if (contactPubKey != null) {
                connectManager.sendMessage(
                    newBoostMessage,
                    contactPubKey,
                    provisionalId.value,
                    MessageType.BOOST,
                    owner.tipAmount?.value ?: 20L,
                    currentChat?.isTribe() ?: false
                )
            }
        }.join()

        return response
    }

    override fun sendBoost(
        chatId: ChatId,
        boost: FeedBoost
    ) {
        applicationScope.launch(mainImmediate) {
            val message = boost.toJson(moshi)

            val sendMessageBuilder = SendMessage.Builder()
            sendMessageBuilder.setChatId(chatId)
            sendMessageBuilder.setText(message)
            sendMessageBuilder.setIsBoost(true)

            sendMessage(
                sendMessageBuilder.build().first
            )
        }
    }

    override suspend fun payContactPaymentRequest(
        paymentRequest: LightningPaymentRequest?
    ) {
        applicationScope.launch(mainImmediate) {
            paymentRequest?.value?.let {
                connectManager.processContactInvoicePayment(it)
            }
        }
    }

    override suspend fun payInvoice(
        paymentRequest: LightningPaymentRequest,
        endHops: String?,
        routerPubKey: String?,
        milliSatAmount: Long,
        isSphinxInvoice: Boolean,
        paymentHash: String?,
        callback: (() -> Unit)?
    ) {
        if (paymentHash != null) {
            webViewPaymentHash.value = paymentHash
        }

        if (endHops?.isNotEmpty() == true && routerPubKey != null) {
            connectManager.concatNodesFromResponse(
                endHops,
                routerPubKey,
                milliSatAmount
            )
        }
        val tag = connectManager.processInvoicePayment(
            paymentRequest.value,
            milliSatAmount
        )

        if (!isSphinxInvoice) {
            tag?.let {
                callback?.invoke()
                processingInvoice.value = Pair(paymentRequest.value, it)

                val timer = Timer("OneTimeTimer", true)
                timer.schedule(object : TimerTask() {
                    override fun run() {
                        processingInvoice.value = null
                    }
                }, 60000)
            }
        }
    }

    override suspend fun payInvoiceFromLSP(paymentRequest: LightningPaymentRequest) {
        connectManager.payInvoiceFromLSP(paymentRequest.value)
    }

    override suspend fun sendKeySend(
        pubKey: String,
        endHops: String?,
        milliSatAmount: Long,
        routerPubKey: String?,
        routeHint: String?,
        data: String?
    ) {
        if (endHops?.isNotEmpty() == true && routerPubKey != null) {
            connectManager.concatNodesFromResponse(
                endHops,
                routerPubKey,
                milliSatAmount
            )
        }
        connectManager.sendKeySend(
            pubKey,
            milliSatAmount,
            routeHint,
            data
        )
    }

    override suspend fun sendKeySendWithRouting(
        pubKey: LightningNodePubKey,
        routeHint: LightningRouteHint?,
        milliSatAmount: MilliSat?,
        routerUrl: String?,
        routerPubKey: String?,
        data: String?
    ): Boolean {
        var owner: Contact? = accountOwner.value

        if (owner == null) {
            try {
                accountOwner.collect {
                    if (it != null) {
                        owner = it
                        throw Exception()
                    }
                }
            } catch (e: Exception) {
            }
            delay(25L)
        }
        val payeeLspPubKey = routeHint?.getLspPubKey()
        val ownerLspPubKey = owner?.routeHint?.getLspPubKey()


        return if (payeeLspPubKey == ownerLspPubKey) {
            sendKeySend(
                pubKey.value,
                null,
                milliSatAmount?.value ?: 0,
                null,
                routeHint?.value,
                data
            )
            true
        } else {
            val isAvailableRoute = isRouteAvailable(
                pubKey.value,
                routerPubKey,
                milliSatAmount?.value ?: 0,
            )
            if (isAvailableRoute) {
                sendKeySend(
                    pubKey.value,
                    null,
                    milliSatAmount?.value ?: 0,
                    null,
                    routeHint?.value,
                    data
                )
                true
            } else {

                if (routerUrl != null) {
                    var success = false
                    networkQueryContact.getRoutingNodes(
                        routerUrl,
                        pubKey,
                        milliSatAmount?.value ?: 0
                    ).collect { response ->
                        when (response) {
                            is LoadResponse.Loading -> {}
                            is Response.Error -> {
                                success = false
                            }

                            is Response.Success -> {
                                if (isJsonResponseEmpty(response.value)) {
                                    sendKeySend(
                                        pubKey.value,
                                        null,
                                        milliSatAmount?.value ?: 0,
                                        null,
                                        routeHint?.value,
                                        data
                                    )
                                } else {
                                    sendKeySend(
                                        pubKey.value,
                                        response.value,
                                        milliSatAmount?.value ?: 0,
                                        routerPubKey,
                                        routeHint?.value,
                                        data
                                    )
                                }
                                success = true
                            }
                        }
                    }
                    success
                } else {
                    false
                }
            }
        }
    }

    override fun isRouteAvailable(pubKey: String, routeHint: String?, milliSat: Long): Boolean {
        return connectManager.isRouteAvailable(pubKey, routeHint, milliSat)
    }

    override fun createInvoice(amount: Long, memo: String): Pair<String, String>? {
        return connectManager.createInvoice(amount, memo)
    }

    private fun isJsonResponseEmpty(json: String?): Boolean {
        return json.isNullOrEmpty()
    }

    override fun clearWebViewPreImage() {
        webViewPreImage.value = null
    }

    override suspend fun sendNewPaymentRequest(requestPayment: SendPaymentRequest) {
        applicationScope.launch(mainImmediate) {
            val queries = coreDB.getSphinxDatabaseQueries()
            val chatId = requestPayment.chatId ?: return@launch
            val contact = requestPayment.contactId?.value?.let { ContactId(it) }
                ?.let { getContactById(it).firstOrNull() }

            val currentProvisionalId: MessageId? = withContext(io) {
                queries.messageGetLowestProvisionalMessageId().executeAsOneOrNull()
            }
            val provisionalId = MessageId((currentProvisionalId?.value ?: 0L) - 1)

            val invoiceAndHash = connectManager.createInvoice(requestPayment.amount, requestPayment.memo ?: "")

            val newPaymentRequest = NewMessage(
                id = provisionalId,
                uuid = null,
                chatId = chatId,
                type = MessageType.Invoice,
                sender = accountOwner.value?.id ?: ContactId(0),
                receiver = null,
                amount = requestPayment.amount.toSat() ?: Sat(0),
                paymentHash = invoiceAndHash?.second?.toLightningPaymentHash(),
                paymentRequest = invoiceAndHash?.first?.toLightningPaymentRequestOrNull(),
                date = DateTime.nowUTC().toDateTime(),
                expirationDate = null,
                messageContent = null,
                status = MessageStatus.Pending,
                seen = Seen.True,
                senderAlias = accountOwner.value?.alias?.value?.toSenderAlias(),
                senderPic = accountOwner.value?.photoUrl,
                originalMUID = null,
                replyUUID = null,
                flagged = false.toFlagged(),
                recipientAlias = null,
                recipientPic = null,
                person = null,
                threadUUID = null,
                errorMessage = null,
                isPinned = false,
                messageContentDecrypted = requestPayment.memo?.toMessageContentDecrypted(),
                messageDecryptionError = false,
                messageDecryptionException = null,
                messageMedia = null,
                feedBoost = null,
                callLinkMessage = null,
                podcastClip = null,
                giphyData = null,
                reactions = null,
                purchaseItems = null,
                replyMessage = null,
                thread = null
            )

            val newPaymentRequestMessage = chat.sphinx.example.wrapper_mqtt.Message(
                requestPayment.memo,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                invoiceAndHash?.first,
                null
            ).toJson(moshi)

            chatLock.withLock {
                messageLock.withLock {
                    withContext(io) {
                        queries.transaction {
                            upsertNewMessage(newPaymentRequest, queries, null)
                        }
                    }

                    queries.transaction {
                        updateChatNewLatestMessage(
                            newPaymentRequest,
                            chatId,
                            latestMessageUpdatedTimeMap,
                            queries
                        )
                    }
                }
            }

            if (contact != null) {
                connectManager.sendMessage(
                    newPaymentRequestMessage,
                    contact.nodePubKey?.value ?: "",
                    provisionalId.value,
                    MessageType.INVOICE,
                     null,
                    false
                )
            }
        }
    }

    override suspend fun payAttachment(message: Message) {
        applicationScope.launch(mainImmediate) {
            val queries = coreDB.getSphinxDatabaseQueries()

            message.messageMedia?.mediaToken?.let { mediaToken ->
                mediaToken.getPriceFromMediaToken().let { price ->

                    val owner: Contact = accountOwner.value
                        ?: let {
                            // TODO: Handle this better...
                            var owner: Contact? = null
                            try {
                                accountOwner.collect {
                                    if (it != null) {
                                        owner = it
                                        throw Exception()
                                    }
                                }
                            } catch (e: Exception) {}
                            delay(25L)
                            owner
                        } ?: return@launch

                    val contact: Contact? = message.chatId.let { chatId ->
                        getContactById(ContactId(chatId.value)).firstOrNull()
                    }

                    val currentChat = getChatById(message.chatId).firstOrNull()

                    val currentProvisionalId: MessageId? = withContext(io) {
                        queries.messageGetLowestProvisionalMessageId().executeAsOneOrNull()
                    }
                    val provisionalId = MessageId((currentProvisionalId?.value ?: 0L) - 1)

                    val newPurchase = NewMessage(
                        id = provisionalId,
                        uuid = null,
                        chatId = message.chatId,
                        type = MessageType.Purchase.Processing,
                        sender = owner.id,
                        receiver = null,
                        amount = price,
                        date = DateTime.nowUTC().toDateTime(),
                        expirationDate = null,
                        messageContent = null,
                        status = MessageStatus.Confirmed,
                        seen = Seen.True,
                        senderAlias = null,
                        senderPic = null,
                        originalMUID = message.originalMUID,
                        replyUUID = null,
                        flagged = false.toFlagged(),
                        recipientAlias = null,
                        recipientPic = null,
                        person = null,
                        threadUUID = null,
                        errorMessage = null,
                        isPinned = false,
                        messageContentDecrypted = null,
                        messageDecryptionError = false,
                        messageDecryptionException = null,
                        messageMedia = null,
                        feedBoost = null,
                        callLinkMessage = null,
                        podcastClip = null,
                        giphyData = null,
                        reactions = null,
                        purchaseItems = null,
                        replyMessage = null,
                        thread = null
                    )

                    val newPurchaseMessage = chat.sphinx.example.wrapper_mqtt.Message(
                        null,
                        null,
                        mediaToken.value,
                        null,
                        null,
                        message.uuid?.value,
                        null,
                        null,
                        null,
                        null,
                    ).toJson(moshi)


                    chatLock.withLock {
                        messageLock.withLock {
                            withContext(io) {
                                queries.transaction {
                                    upsertNewMessage(newPurchase, queries, null)
                                }
                            }

                            queries.transaction {
                                updateChatNewLatestMessage(
                                    newPurchase,
                                    message.chatId,
                                    latestMessageUpdatedTimeMap,
                                    queries
                                )
                            }
                        }
                    }

                    val contactPubKey = contact?.nodePubKey?.value ?: currentChat?.uuid?.value

                    if (contactPubKey != null) {
                        connectManager.sendMessage(
                            newPurchaseMessage,
                            contactPubKey,
                            provisionalId.value,
                            MessageType.PURCHASE_PROCESSING,
                            price.value,
                            currentChat?.isTribe() ?: false
                        )
                    }
                }
            }
        }
    }

    override suspend fun setNotificationLevel(
        chat: Chat,
        level: NotificationLevel
    ): Response<Boolean, ResponseError> {
        var response: Response<Boolean, ResponseError> = Response.Success(level.isMuteChat())

        applicationScope.launch(mainImmediate) {
            val queries = coreDB.getSphinxDatabaseQueries()
            val contact = queries.contactGetById(ContactId(chat.id.value)).executeAsOneOrNull()

            if (contact != null) {
                contact.node_pub_key?.value?.let { pubKey ->
                    connectManager.setMute(level.value, pubKey)
                }
            } else {
                connectManager.setMute(level.value, chat.uuid.value)
            }

            chatLock.withLock {
                withContext(io) {
                    queries.transaction {
                        updateChatNotificationLevel(
                            chat.id,
                            level,
                            queries
                        )
                    }
                }
            }
        }.join()

        return response
    }

    override suspend fun updateChatContentSeenAt(chatId: ChatId) {
        val queries = coreDB.getSphinxDatabaseQueries()

        chatLock.withLock {
            withContext(io) {
                queries.chatUpdateContentSeenAt(
                    DateTime(Date()),
                    chatId
                )
            }
        }
    }

    override fun getAllDiscoverTribes(
        page: Int,
        itemsPerPage: Int,
        searchTerm: String?,
        tags: String?,
        tribeServer: String?
    ): Flow<List<NewTribeDto>> = flow {
        networkQueryDiscoverTribes.getAllDiscoverTribes(
            page,
            itemsPerPage,
            searchTerm,
            tags,
            tribeServer
        ).collect { response ->
            @Exhaustive
            when(response) {
                is LoadResponse.Loading -> {}
                is Response.Error -> {}
                is Response.Success -> {
                    emit(response.value)
                }
            }
        }
    }

    override suspend fun updateTribeInfo(chat: Chat, isProductionEnvironment: Boolean): NewTribeDto? {
        var owner: Contact? = accountOwner.value

        if (owner == null) {
            try {
                accountOwner.collect {
                    if (it != null) {
                        owner = it
                        throw Exception()
                    }
                }
            } catch (e: Exception) {
            }
            delay(25L)
        }

        var tribeData: NewTribeDto? = null

        chat.host?.let { chatHost ->
            val chatUUID = chat.uuid

            if (chat.isTribe() &&
                chatHost.toString().isNotEmpty() &&
                chatUUID.toString().isNotEmpty()
            ) {
                val queries = coreDB.getSphinxDatabaseQueries()

                networkQueryChat.getTribeInfo(chatHost, LightningNodePubKey(chatUUID.value), isProductionEnvironment)
                    .collect { loadResponse ->
                        when (loadResponse) {

                            is LoadResponse.Loading -> {}
                            is Response.Error -> {}
                            is Response.Success -> {
                                tribeData = loadResponse.value

                                if (owner?.nodePubKey != chat.ownerPubKey) {

                                    chatLock.withLock {
                                        queries.transaction {
                                            updateNewChatTribeData(loadResponse.value, chat.id, queries)
                                        }
                                    }
                                }
                            }
                        }
                    }
            }
        }
        return tribeData
    }

    private val feedLock = Mutex()
    override suspend fun updateFeedContent(
        chatId: ChatId,
        host: ChatHost,
        feedUrl: FeedUrl,
        searchResultDescription: FeedDescription?,
        searchResultImageUrl: PhotoUrl?,
        chatUUID: ChatUUID?,
        subscribed: Subscribed,
        currentItemId: FeedId?,
        delay: Long
    ): Response<FeedId, ResponseError> {
        val queries = coreDB.getSphinxDatabaseQueries()

        var updateResponse: Response<FeedId, ResponseError> = Response.Error(ResponseError("Feed content update failed"))

        networkQueryChat.getFeedContent(
            host,
            feedUrl,
            chatUUID
        ).collect { response ->
            @Exhaustive
            when (response) {
                is LoadResponse.Loading -> {}

                is Response.Error -> {
                    updateResponse = response
                }
                is Response.Success -> {

                    var cId: ChatId = chatId
                    val feedId = response.value.fixedId.toFeedId()

                    feedId?.let { feedId ->
                        queries.feedGetByIds(
                            feedId.youtubeFeedIds()
                        ).executeAsOneOrNull()
                            ?.let { existingFeed ->
                                //If feed already exists linked to a chat, do not override with NULL CHAT ID
                                if (chatId.value == ChatId.NULL_CHAT_ID.toLong()) {
                                    cId = existingFeed.chat_id
                                }
                            }
                    }

                    feedLock.withLock {
                        queries.transaction {
                            upsertFeed(
                                response.value,
                                feedUrl,
                                searchResultDescription,
                                searchResultImageUrl,
                                cId,
                                subscribed,
                                currentItemId,
                                queries
                            )
                        }
                    }

                    delay(delay)

                    updateResponse = feedId?.let {
                        Response.Success(it)
                    } ?: run {
                       Response.Error(ResponseError("Feed content update failed"))
                    }
                }
            }
        }

        return updateResponse
    }

    private suspend fun updateFeedContentItemsFor(
        feed: Feed,
        host: ChatHost,
        durationRetrieverHandler: ((url: String) -> Long)? = null
    ): Response<Any, ResponseError> {
        val queries = coreDB.getSphinxDatabaseQueries()

        var updateResponse: Response<Any, ResponseError> = Response.Error(ResponseError("Feed content items update failed"))

        networkQueryChat.getFeedContent(
            host,
            feed.feedUrl,
            null
        ).collect { response ->
            @Exhaustive
            when (response) {
                is LoadResponse.Loading -> {}

                is Response.Error -> {
                    updateResponse = response
                }
                is Response.Success -> {

                    feedLock.withLock {
                        queries.transaction {
                            upsertFeedItems(
                                response.value,
                                queries
                            )
                        }
                    }

                    for (item in response.value.items.take(5)) {

                        val episodeStatus = feed.items.firstOrNull { it.id.value == item.id }?.let {
                            it.contentEpisodeStatus
                        }

                        if (episodeStatus == null) {
                            (durationRetrieverHandler?.let { it(item.enclosureUrl) })?.let { duration ->
                                updateContentEpisodeStatusDuration(
                                    FeedId(item.id),
                                    feed.id,
                                    FeedItemDuration(duration / 1000),
                                    queries
                                )
                            }
                        }
                    }

                    updateResponse = Response.Success(true)
                }
            }
        }

        return updateResponse
    }

    override fun getFeedByChatId(chatId: ChatId): Flow<Feed?> = flow {
        val queries = coreDB.getSphinxDatabaseQueries()

        queries.feedGetByChatId(chatId)
            .asFlow()
            .mapToOneOrNull(io)
            .map { it?.let { feedDboPresenterMapper.mapFrom(it) } }
            .distinctUntilChanged()
            .collect { value: Feed? ->
                value?.let { feed ->
                    emit(
                        mapFeedDbo(
                            feed,
                            queries
                        )
                    )
                }
            }
    }

    override fun getFeedById(feedId: FeedId): Flow<Feed?> = flow {
        val queries = coreDB.getSphinxDatabaseQueries()

        queries.feedGetByIds(feedId.youtubeFeedIds())
            .asFlow()
            .mapToOneOrNull(io)
            .map { it?.let { feedDboPresenterMapper.mapFrom(it) } }
            .distinctUntilChanged()
            .collect { value: Feed? ->
                value?.let { feed ->
                    emit(
                        mapFeedDbo(
                            feed,
                            queries
                        )
                    )
                } ?: run {
                    emit(null)
                }
            }
    }

    override fun getFeedItemById(feedItemId: FeedId): Flow<FeedItem?> = flow {
        val queries = coreDB.getSphinxDatabaseQueries()

        queries.feedItemGetById(feedItemId)
            .asFlow()
            .mapToOneOrNull(io)
            .map { it?.let { feedItemDboPresenterMapper.mapFrom(it) } }
            .distinctUntilChanged()
            .collect { value: FeedItem? ->
                value?.let { feedItem ->
                    emit(
                        mapFeedItemDbo(feedItem, queries)
                    )
                } ?: run {
                    emit(null)
                }
            }
    }

    override fun getFeedForLink(link: FeedItemLink): Flow<Feed?> = flow {
        link.feedId.toFeedId()?.let { getFeedById(it) }?.firstOrNull()?.let { feed ->
            feedUpdateItemAndTime(feed, link)
            emit(feed)
        } ?: run {
            link.feedUrl.toFeedUrl()?.let { feedUrl ->
                val response = updateFeedContent(
                    chatId = ChatId(ChatId.NULL_CHAT_ID.toLong()),
                    host = ChatHost(Feed.TRIBES_DEFAULT_SERVER_URL),
                    feedUrl = feedUrl,
                    chatUUID = null,
                    subscribed = false.toSubscribed(),
                    currentItemId = null
                )
                @Exhaustive
                when (response) {
                    is Response.Error -> {
                        emit(null)
                    }
                    is Response.Success -> {
                        getFeedById(response.value).firstOrNull()?.let { feed ->
                            feedUpdateItemAndTime(feed, link)
                            emit(feed)
                        }  ?: emit(null)
                    }
                }
            } ?: emit(null)
        }
    }

    private fun feedUpdateItemAndTime(
        feed: Feed,
        link: FeedItemLink
    ) {
        link.itemId.toFeedId()?.let { itemId ->

            updateContentFeedStatus(
                feed.id,
                itemId
            )

            link.atTime?.let { atTime ->
                feed.items.firstOrNull {
                    it.id == itemId
                }?.let { feedItem ->
                    updateContentEpisodeStatus(
                        feedId = feed.id,
                        itemId = itemId,
                        duration = feedItem.contentEpisodeStatus?.duration ?: FeedItemDuration(0),
                        currentTime =atTime.toLong().toFeedItemDuration() ?: FeedItemDuration(0),
                        played = feedItem.contentEpisodeStatus?.played ?: false,
                        shouldSync = false
                    )
                }
            }
        }
    }

    override fun getRecommendationFeedItemById(
        feedItemId: FeedId,
    ): Flow<FeedItem?> = flow {
        recommendationsPodcast.value?.getEpisodeWithId(feedItemId.value)?.let { episode ->
            val item = FeedItem(
                episode.id,
                episode.description?.value?.toFeedTitle() ?: FeedTitle(""),
                episode.title.value.toFeedDescription(),
                episode.date,
                episode.date,
                null,
                episode.feedType.toFeedContentType(),
                null,
                episode.link ?: FeedUrl(""),
                null,
                episode.imageUrlToShow,
                episode.imageUrlToShow,
                episode.link ?: FeedUrl(""),
                FeedId(FeedRecommendation.RECOMMENDATION_PODCAST_ID),
                FeedItemDuration(0),
                null,
                null,
                null
            )

            item.showTitle = episode.showTitle?.value
            item.feedType = episode.feedType.toFeedType()

            emit(
                item
            )
        }
    }

    override fun getAllDownloadedFeedItems(): Flow<List<FeedItem>> = flow {
        emitAll(
            coreDB.getSphinxDatabaseQueries().feedItemGetAllDownloaded(::FeedItemDbo)
                .asFlow()
                .mapToList(io)
                .map { listFeedItemDbo ->
                    mapFeedItemDboList(
                        listFeedItemDbo,
                        coreDB.getSphinxDatabaseQueries()
                    )
                }
                .distinctUntilChanged()
        )
    }

    override fun getDownloadedFeedItemsByFeedId(feedId: FeedId): Flow<List<FeedItem>> = flow {
        emitAll(
            coreDB.getSphinxDatabaseQueries().feedItemGetDownloadedByFeedId(feedId, ::FeedItemDbo)
                .asFlow()
                .mapToList(io)
                .map { listFeedItemDbo ->
                    listFeedItemDbo.map {
                        feedItemDboPresenterMapper.mapFrom(it)
                    }
                }
                .distinctUntilChanged()
        )
    }

    private val feedDboPresenterMapper: FeedDboPresenterMapper by lazy {
        FeedDboPresenterMapper(dispatchers)
    }
    private val feedItemDboPresenterMapper: FeedItemDboPresenterMapper by lazy {
        FeedItemDboPresenterMapper(dispatchers)
    }
    private val feedModelDboPresenterMapper: FeedModelDboPresenterMapper by lazy {
        FeedModelDboPresenterMapper(dispatchers)
    }
    private val feedDestinationDboPresenterMapper: FeedDestinationDboPresenterMapper by lazy {
        FeedDestinationDboPresenterMapper(dispatchers)
    }
    private val contentFeedStatusDboPresenterMapper: ContentFeedStatusDboPresenterMapper by lazy {
        ContentFeedStatusDboPresenterMapper(dispatchers)
    }
    private val contentEpisodeStatusDboPresenterMapper: ContentEpisodeStatusDboPresenterMapper by lazy {
        ContentEpisodeStatusDboPresenterMapper(dispatchers)
    }

    override fun getAllFeedsOfType(feedType: FeedType): Flow<List<Feed>> = flow {
        val queries = coreDB.getSphinxDatabaseQueries()

        emitAll(
            queries.feedGetAllByFeedType(feedType)
                .asFlow()
                .mapToList(io)
                .map { listFeedDbo ->
                    withContext(default) {
                        mapFeedDboList(
                            listFeedDbo, queries
                        )
                    }
                }
        )
    }

    override fun getAllSubscribedFeedsOfType(feedType: FeedType): Flow<List<Feed>> = flow {
        val queries = coreDB.getSphinxDatabaseQueries()

        emitAll(
            queries.feedGetAllSubscribedByFeedType(feedType)
                .asFlow()
                .mapToList(io)
                .map { listFeedDbo ->
                    withContext(default) {
                        mapFeedDboList(
                            listFeedDbo, queries
                        )
                    }
                }
        )
    }

    override fun getAllFeeds(): Flow<List<Feed>> = flow {
        val queries = coreDB.getSphinxDatabaseQueries()
        emitAll(
            queries.feedGetAll()
                .asFlow()
                .mapToList(io)
                .map { listFeedDbo ->
                    withContext(default) {
                        mapFeedDboList(
                            listFeedDbo, queries
                        )
                    }
                }
        )
    }

    override fun getAllSubscribedFeeds(): Flow<List<Feed>> = flow {
        val queries = coreDB.getSphinxDatabaseQueries()
        emitAll(
            queries.feedGetAllSubscribed()
                .asFlow()
                .mapToList(io)
                .map { listFeedDbo ->
                    withContext(default) {
                        mapFeedDboList(
                            listFeedDbo, queries
                        )
                    }
                }
        )
    }

    override val recommendationsPodcast: MutableStateFlow<Podcast?> by lazy {
        MutableStateFlow(null)
    }

    override fun getRecommendedFeeds(): Flow<List<FeedRecommendation>> = flow {

        var results: MutableList<FeedRecommendation> = mutableListOf()

        applicationScope.launch(mainImmediate) {
//            networkQueryFeedSearch.getFeedRecommendations().collect { response ->
//                @Exhaustive
//                when (response) {
//                    is LoadResponse.Loading -> {}
//                    is Response.Error -> {}
//                    is Response.Success -> {
//                        response.value.forEachIndexed { index, feedRecommendation ->
//                            results.add(
//                                FeedRecommendation(
//                                    id = feedRecommendation.ref_id,
//                                    pubKey = feedRecommendation.pub_key,
//                                    feedType = feedRecommendation.type,
//                                    description = feedRecommendation.description,
//                                    smallImageUrl = feedRecommendation.s_image_url,
//                                    mediumImageUrl = feedRecommendation.m_image_url,
//                                    largeImageUrl = feedRecommendation.l_image_url,
//                                    link = feedRecommendation.link,
//                                    title = feedRecommendation.episode_title,
//                                    showTitle = feedRecommendation.show_title,
//                                    date = feedRecommendation.date,
//                                    timestamp = feedRecommendation.timestamp,
//                                    topics = feedRecommendation.topics,
//                                    guests = feedRecommendation.guests,
//                                    position = index + 1
//                                )
//                            )
//                        }
//                    }
//                }
//            }
        }.join()

        recommendationsPodcast.value = mapRecommendationsPodcast(results)

        emit(results)
    }

    private val feedRecommendationPodcastPresenterMapper: FeedRecommendationPodcastPresenterMapper by lazy {
        FeedRecommendationPodcastPresenterMapper()
    }

    private fun mapRecommendationsPodcast(
        recommendations: List<FeedRecommendation>
    ): Podcast? {
        val podcast = feedRecommendationPodcastPresenterMapper.getRecommendationsPodcast()

        podcast.episodes = recommendations.map {
            feedRecommendationPodcastPresenterMapper.mapFrom(
                it,
                podcast.id
            )
        }.sortedByDescending { it.datePublishedTime }

        if (podcast.episodes.isEmpty()) {
            return null
        }

        return podcast
    }

    private suspend fun mapFeedDboList(
        listFeedDbo: List<FeedDbo>,
        queries: SphinxDatabaseQueries
    ): List<Feed> {

        val itemsMap: MutableMap<FeedId, ArrayList<FeedItem>> =
            LinkedHashMap(listFeedDbo.size)

        val chatsMap: MutableMap<ChatId, Chat?> =
            LinkedHashMap(listFeedDbo.size)

        val contentFeedStatusMap: MutableMap<FeedId, ContentFeedStatus?> =
            LinkedHashMap(listFeedDbo.size)

        val contentEpisodeStatusesMap: MutableMap<FeedId, ArrayList<ContentEpisodeStatus>> =
            LinkedHashMap(listFeedDbo.size)

        for (dbo in listFeedDbo) {
            chatsMap[dbo.chat_id] = null
            contentFeedStatusMap[dbo.id] = null

            itemsMap[dbo.id] = ArrayList(0)
            contentEpisodeStatusesMap[dbo.id] = ArrayList(0)
        }

        itemsMap.keys.chunked(500).forEach { chunkedIds ->
            queries.feedItemsGetByFeedIds(chunkedIds)
                .executeAsList()
                .let { response ->
                    response.forEach { dbo ->
                        dbo.feed_id?.let { feedId ->
                            itemsMap[feedId]?.add(
                                feedItemDboPresenterMapper.mapFrom(dbo)
                            )
                        }
                    }
                }
        }

        chatsMap.keys.chunked(500).forEach { chunkedChatIds ->
            queries.chatGetAllByIds(chunkedChatIds)
                .executeAsList()
                .let { response ->
                    response.forEach { dbo ->
                        dbo.id?.let { chatId ->
                            chatsMap[chatId] = chatDboPresenterMapper.mapFrom(dbo)
                        }
                    }
                }
        }

        contentFeedStatusMap.keys.chunked(500).forEach { chunkedIds ->
            queries.contentFeedStatusGetByIds(chunkedIds)
                .executeAsList()
                .let { response ->
                    response.forEach { dbo ->
                        dbo.feed_id?.let { feedId ->
                            contentFeedStatusMap[feedId] = contentFeedStatusDboPresenterMapper.mapFrom(dbo)
                        }
                    }
                }
        }

        contentEpisodeStatusesMap.keys.chunked(500).forEach { chunkedIds ->
            queries.contentEpisodeStatusGetByFeedIds(chunkedIds)
                .executeAsList()
                .let { response ->
                    response.forEach { dbo ->
                        dbo.feed_id?.let { feedId ->
                            contentEpisodeStatusesMap[feedId]?.add(
                                contentEpisodeStatusDboPresenterMapper.mapFrom(dbo)
                            )
                        }
                    }
                }
        }

        val list = listFeedDbo.map {
            mapFeedDbo(
                feedDbo = it,
                items = itemsMap[it.id] ?: listOf(),
                model = null,
                destinations = listOf(),
                chat = chatsMap[it.chat_id],
                contentFeedStatus = contentFeedStatusMap[it.id],
                contentEpisodeStatus = contentEpisodeStatusesMap[it.id] ?: listOf()
            )
        }

        return list
    }

    private suspend fun mapFeedDbo(
        feedDbo: FeedDbo,
        items: List<FeedItem>,
        model: FeedModel? = null,
        destinations: List<FeedDestination>,
        chat: Chat? = null,
        contentFeedStatus: ContentFeedStatus? = null,
        contentEpisodeStatus: List<ContentEpisodeStatus>
    ): Feed {

        val feed = feedDboPresenterMapper.mapFrom(feedDbo)

        items.forEach { feedItem ->
            feedItem.feed = feed

            contentEpisodeStatus.forEach { contentEpisodeStatus ->
                if (feedItem.id == contentEpisodeStatus.itemId) {
                    feedItem.contentEpisodeStatus = contentEpisodeStatus
                }
            }
        }

        feed.items = items
        feed.model = model
        feed.destinations = destinations
        feed.chat = chat
        feed.contentFeedStatus = contentFeedStatus

        return feed
    }

    private suspend fun mapFeedDbo(
        feed: Feed,
        queries: SphinxDatabaseQueries
    ): Feed {

        val model = queries.feedModelGetById(feed.id).executeAsOneOrNull()?.let { feedModelDbo ->
            feedModelDboPresenterMapper.mapFrom(feedModelDbo)
        }

        val chat = queries.chatGetById(feed.chatId).executeAsOneOrNull()?.let { chatDbo ->
            chatDboPresenterMapper.mapFrom(chatDbo)
        }

        val items = queries.feedItemsGetByFeedId(feed.id).executeAsList().map {
            feedItemDboPresenterMapper.mapFrom(it)
        }

        val destinations = queries.feedDestinationsGetByFeedId(feed.id).executeAsList().map {
            feedDestinationDboPresenterMapper.mapFrom(it)
        }

        val contentFeedStatus = queries.contentFeedStatusGetByFeedId(feed.id).executeAsOneOrNull()?.let { contentFeedStatus ->
            contentFeedStatusDboPresenterMapper.mapFrom(contentFeedStatus)
        }

        val itemIds = items.map { it.id }

        val contentEpisodeStatuses = queries.contentEpisodeStatusGetByFeedIdAndItemIds(feed.id, itemIds).executeAsList().map {
            contentEpisodeStatusDboPresenterMapper.mapFrom(it)
        }

        items.forEach { feedItem ->
            feedItem.feed = feed

            contentEpisodeStatuses.forEach { contentEpisodeStatus ->
                if (feedItem.id == contentEpisodeStatus.itemId) {
                    feedItem.contentEpisodeStatus = contentEpisodeStatus
                }
            }
        }

        feed.items = items
        feed.model = model
        feed.destinations = destinations
        feed.chat = chat
        feed.contentFeedStatus = contentFeedStatus

        return feed
    }

    private suspend fun mapFeedItemDbo(
        feedItem: FeedItem,
        queries: SphinxDatabaseQueries
    ): FeedItem {

        var feed = queries.feedGetById(feedItem.feedId).executeAsOneOrNull()?.let {
            feedDboPresenterMapper.mapFrom(it)
        }

        feed?.let {
            feed = mapFeedDbo(it, queries)
        }

        val contentEpisodeStatus = queries.contentEpisodeStatusGetByFeedIdAndItemId(feedItem.feedId, feedItem.id).executeAsOneOrNull()?.let {
            contentEpisodeStatusDboPresenterMapper.mapFrom(it)
        }

        feedItem.contentEpisodeStatus = contentEpisodeStatus
        feedItem.feed = feed

        return feedItem
    }

    private suspend fun mapFeedItemDboList(
        listFeedItemDbo: List<FeedItemDbo>,
        queries: SphinxDatabaseQueries
    ): List<FeedItem> {

        val feedsMap: MutableMap<FeedId, Feed> = mutableMapOf()
        val feedIds = listFeedItemDbo.map { it.feed_id }.distinct()

        queries.feedGetByIds(feedIds)
            .executeAsList()
            .let { response ->
                response.forEach { dbo ->
                    val feed = feedDboPresenterMapper.mapFrom(dbo)
                    feedsMap[dbo.id] = feed
                }
            }

        val feedItems = listFeedItemDbo.map {
            feedItemDboPresenterMapper.mapFrom(it).apply {
                it.feed_id
            }
        }

        feedItems.forEach { item ->
            item.feed = feedsMap[item.feedId]
        }

        return feedItems
    }

    private val podcastDboPresenterMapper: FeedDboPodcastPresenterMapper by lazy {
        FeedDboPodcastPresenterMapper(dispatchers)
    }

    private val podcastEpisodeDboPresenterMapper: FeedItemDboPodcastEpisodePresenterMapper by lazy {
        FeedItemDboPodcastEpisodePresenterMapper(dispatchers)
    }

    override fun getPodcastByChatId(chatId: ChatId): Flow<Podcast?> = flow {
        val queries = coreDB.getSphinxDatabaseQueries()

        queries.feedGetByChatIdAndType(chatId, FeedType.Podcast)
            .asFlow()
            .mapToOneOrNull(io)
            .map { it?.let { podcastDboPresenterMapper.mapFrom(it) } }
            .distinctUntilChanged()
            .collect { value: Podcast? ->
                value?.let { podcast ->
                    emit(
                        mapPodcast(podcast, queries)
                    )
                }
            }
    }

    override fun getPodcastById(feedId: FeedId): Flow<Podcast?> = flow {
        if (feedId.value == FeedRecommendation.RECOMMENDATION_PODCAST_ID) {
            emit(recommendationsPodcast.value)
            return@flow
        }

        val queries = coreDB.getSphinxDatabaseQueries()

        queries.feedGetById(feedId)
            .asFlow()
            .mapToOneOrNull(io)
            .map { it?.let { podcastDboPresenterMapper.mapFrom(it) } }
            .distinctUntilChanged()
            .collect { value: Podcast? ->
                value?.let { podcast ->
                    emit(
                        mapPodcast(podcast, queries)
                    )
                }
            }
    }

    override suspend fun checkIfEpisodeNodeExists(
        podcastEpisode: PodcastEpisode,
        podcastTitle: FeedTitle,
        workflowId: Int?,
        token: String?
    ) {
        networkQueryFeedSearch.checkIfEpisodeNodeExists(podcastEpisode, podcastTitle).collect { response ->
            @Exhaustive
            when (response) {
                is LoadResponse.Loading -> {}
                is Response.Error -> {}
                is Response.Success -> {
                    val queries = coreDB.getSphinxDatabaseQueries()
                    val referenceId = response.value.data?.ref_id?.toFeedReferenceId()

                    queries.feedItemUpdateReferenceId(referenceId, podcastEpisode.id)

                    if (response.value.errorCode?.contains("already exists") == true ||
                        response.value.node_key != null
                    ) {
                        getChaptersData(podcastEpisode, podcastTitle, referenceId!!, podcastEpisode.id, workflowId, token)
                    } else if (response.value.success == true) {

                        if (workflowId != null && token != null && referenceId != null) {

                            createProjectTimestamps.value[podcastEpisode.id.value] = System.currentTimeMillis()

                            networkQueryFeedSearch.createStakworkProject(
                                podcastEpisode,
                                podcastTitle,
                                workflowId,
                                token,
                                referenceId
                            ).collect { projectResponse ->
                                when (projectResponse) {
                                    is LoadResponse.Loading -> {}
                                    is Response.Error -> {}
                                    is Response.Success -> {}
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override suspend fun getEpisodeNodeDetails(
        podcastEpisode: PodcastEpisode,
        podcastTitle: FeedTitle,
        referenceId: FeedReferenceId,
        workflowId: Int?,
        token: String?
    ) {
        networkQueryFeedSearch.getEpisodeNodeDetails(referenceId).collect { episodeResponse ->
            when (episodeResponse) {
                is LoadResponse.Loading -> {}
                is Response.Error -> {}
                is Response.Success -> {
                    val hasProjectId =  episodeResponse.value.properties?.project_id?.isNotEmpty() == true
                    if (!hasProjectId) {
                        if (workflowId != null && token != null) {

                            createProjectTimestamps.value[podcastEpisode.id.value] = System.currentTimeMillis()

                            networkQueryFeedSearch.createStakworkProject(
                                podcastEpisode,
                                podcastTitle,
                                workflowId,
                                token,
                                referenceId
                            ).collect { projectResponse ->
                                when (projectResponse) {
                                    is LoadResponse.Loading -> {}
                                    is Response.Error -> {}
                                    is Response.Success -> {}
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    override suspend fun getChaptersData(
        podcastEpisode: PodcastEpisode,
        podcastTitle: FeedTitle,
        referenceId: FeedReferenceId,
        id: FeedId,
        workflowId: Int?,
        token: String?
    ) {

        val lastProjectTimestamp = createProjectTimestamps.value[podcastEpisode.id.value]
        val currentTime = System.currentTimeMillis()

        if (lastProjectTimestamp != null && currentTime - lastProjectTimestamp < 60 * 60 * 1000) {
            return
        }

        networkQueryFeedSearch.getChaptersData(referenceId).collect { response ->
            @Exhaustive
            when (response) {
                is LoadResponse.Loading -> {}
                is Response.Error -> {}
                is Response.Success -> {
                    val queries = coreDB.getSphinxDatabaseQueries()

                    try {
                        val moshi = Moshi.Builder()
                            .add(KotlinJsonAdapterFactory())
                            .build()

                        val adapter = moshi.adapter(ChapterResponseDto::class.java)
                        val chapterResponseDto = response.value

                        val hasChapters = chapterResponseDto.nodes.any { it.node_type == "Chapter" }

                        if (hasChapters) {
                            val feedChaptersData =
                                adapter.toJson(chapterResponseDto).toFeedChapterData()
                            queries.feedItemUpdateChaptersData(feedChaptersData, id)
                        } else {
                            getEpisodeNodeDetails(
                                podcastEpisode,
                                podcastTitle,
                                referenceId,
                                workflowId,
                                token
                            )
                        }

                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }


    private suspend fun mapPodcast(
        podcast: Podcast,
        queries: SphinxDatabaseQueries
    ): Podcast {

        queries.feedModelGetById(podcast.id).executeAsOneOrNull()?.let { feedModelDbo ->
            podcast.model = feedModelDboPresenterMapper.mapFrom(feedModelDbo)
        }

        val episodes = queries.feedItemsGetByFeedId(podcast.id).executeAsList().map {
            podcastEpisodeDboPresenterMapper.mapFrom(it, podcast)
        }

        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        val adapter = moshi.adapter(ChapterResponseDto::class.java).lenient()

        episodes.forEach { episode ->
            episode.chaptersData?.value?.let { chaptersJson ->
                try {
                    val parsedChapters: ChapterResponseDto? = adapter.fromJson(chaptersJson)
                    episode.chapters = parsedChapters
                } catch (e: Exception) {
                    episode.chapters = null
                }
            }
        }

        val destinations = queries.feedDestinationsGetByFeedId(podcast.id).executeAsList().map {
            feedDestinationDboPresenterMapper.mapFrom(it)
        }

        val contentFeedStatus = queries.contentFeedStatusGetByFeedId(podcast.id).executeAsOneOrNull()?.let {
            contentFeedStatusDboPresenterMapper.mapFrom(it)
        }

        val chat = queries.chatGetById(podcast.chatId).executeAsOneOrNull()?.let {
            chatDboPresenterMapper.mapFrom(it)
        }

        val allContentStatuses = queries.contentEpisodeStatusGetAll().executeAsList()

        val episodeIds = episodes.map { it.id }

        val contentEpisodeStatuses = queries.contentEpisodeStatusGetByFeedIdAndItemIds(podcast.id, episodeIds).executeAsList().map {
            contentEpisodeStatusDboPresenterMapper.mapFrom(it)
        }

        LOG.d("TEST", "${allContentStatuses.count()}")

        if (contentEpisodeStatuses.isNotEmpty()) {
            episodes.forEach { episode ->
                contentEpisodeStatuses.forEach { contentEpisodeStatus ->
                    if (episode.id == contentEpisodeStatus.itemId) {
                        episode.contentEpisodeStatus = contentEpisodeStatus
                    }
                }
            }
        }

        podcast.episodes = episodes
        podcast.destinations = destinations
        podcast.contentFeedStatus = contentFeedStatus
        podcast.chat = chat

        return podcast
    }

    private val feedSearchResultDboPresenterMapper: FeedDboFeedSearchResultPresenterMapper by lazy {
        FeedDboFeedSearchResultPresenterMapper(dispatchers)
    }

    private suspend fun getSubscribedItemsBy(
        searchTerm: String,
        feedType: FeedType?
    ): MutableList<FeedSearchResultRow> {
        val queries = coreDB.getSphinxDatabaseQueries()
        var results: MutableList<FeedSearchResultRow> = mutableListOf()

        val subscribedItems = if (feedType == null) {
            queries
                .feedGetSubscribedByTitle("%${searchTerm.lowercase().trim()}%")
                .executeAsList()
                .map { it?.let { feedSearchResultDboPresenterMapper.mapFrom(it) } }
        } else {
            queries
                .feedGetSubscribedByTitleAndType("%${searchTerm.lowercase().trim()}%", feedType)
                .executeAsList()
                .map { it?.let { feedSearchResultDboPresenterMapper.mapFrom(it) } }
        }


        if (subscribedItems.isNotEmpty()) {
            results.add(
                FeedSearchResultRow(
                    feedSearchResult = null,
                    isSectionHeader = true,
                    isFollowingSection = true,
                    isLastOnSection = false
                )
            )

            subscribedItems.forEachIndexed { index, item ->
                results.add(
                    FeedSearchResultRow(
                        item,
                        isSectionHeader = false,
                        isFollowingSection = true,
                        (index == subscribedItems.count() - 1)
                    )
                )
            }
        }

        return results
    }

    override fun searchFeedsBy(
        searchTerm: String,
        feedType: FeedType?,
    ): Flow<List<FeedSearchResultRow>> = flow {
        if (feedType == null) {
            emit(
                getSubscribedItemsBy(searchTerm, feedType)
            )
            return@flow
        }

        var results: MutableList<FeedSearchResultRow> = mutableListOf()

        networkQueryFeedSearch.searchFeeds(
            searchTerm,
            feedType
        ).collect { response ->
            @Exhaustive
            when (response) {
                is LoadResponse.Loading -> {}

                is Response.Error -> {
                    results.addAll(
                        getSubscribedItemsBy(searchTerm, feedType)
                    )
                }
                is Response.Success -> {

                    results.addAll(
                        getSubscribedItemsBy(searchTerm, feedType)
                    )

                    if (response.value.isNotEmpty()) {
                        results.add(
                            FeedSearchResultRow(
                                feedSearchResult = null,
                                isSectionHeader = true,
                                isFollowingSection = false,
                                isLastOnSection = false
                            )
                        )

                        response.value.forEachIndexed { index, item ->
                            results.add(
                                FeedSearchResultRow(
                                    item.toFeedSearchResult(),
                                    isSectionHeader = false,
                                    isFollowingSection = false,
                                    (index == response.value.count() - 1)
                                )
                            )
                        }
                    }
                }
            }
        }

        emit(results)
    }

    override suspend fun toggleFeedSubscribeState(
        feedId: FeedId,
        currentSubscribeState: Subscribed
    ) {
        val queries = coreDB.getSphinxDatabaseQueries()
        val newValue = if (currentSubscribeState.isTrue()) Subscribed.False else Subscribed.True

        queries.transaction {
            updateSubscriptionStatus(
                queries,
                newValue,
                feedId
            )
        }
    }

    /*
* Used to hold in memory the chat table's latest message time to reduce disk IO
* and mitigate conflicting updates between SocketIO and networkRefreshMessages
* */
    @Suppress("RemoveExplicitTypeArguments")
    private val latestMessageUpdatedTimeMap: SynchronizedMap<ChatId, DateTime> by lazy {
        SynchronizedMap<ChatId, DateTime>()
    }

    override suspend fun didCancelRestore() {
        val now = DateTime.getFormatRelay().format(
            Date(DateTime.getToday00().time)
        )

        authenticationStorage.putString(
            REPOSITORY_LAST_SEEN_MESSAGE_DATE,
            now
        )

        authenticationStorage.removeString(REPOSITORY_LAST_SEEN_MESSAGE_RESTORE_PAGE)
    }

    @OptIn(UnencryptedDataAccess::class)
    private suspend fun decryptMessageDtoContentIfAvailable(
        message: MessageDto,
        scope: CoroutineScope,
        dispatcher: CoroutineDispatcher = mainImmediate
    ): Job? =
        message.message_content?.let { content ->

            if (content.isNotEmpty() && message.type != MessageType.KEY_SEND) {

                scope.launch(dispatcher) {
                    val decrypted = decryptMessageContent(
                        MessageContent(content)
                    )

                    @Exhaustive
                    when (decrypted) {
                        is Response.Error -> {
                            // Only log it if there is an exception
                            decrypted.exception?.let { nnE ->
                                LOG.e(
                                    TAG,
                                    """
                            ${decrypted.message}
                            MessageId: ${message.id}
                            MessageContent: ${message.message_content}
                        """.trimIndent(),
                                    nnE
                                )
                            }
                        }
                        is Response.Success -> {
                            message.setMessageContentDecrypted(
                                decrypted.value.toUnencryptedString(trim = false).value
                            )
                        }
                    }
                }

            } else {
                null
            }
        }

    @OptIn(UnencryptedDataAccess::class)
    private suspend fun decryptMessageMediaKeyIfAvailable(
        messageId: Long,
        mediaKey: String?,
        scope: CoroutineScope,
        dispatcher: CoroutineDispatcher = mainImmediate,
    ): String? = mediaKey?.let { mk ->

        if (mk.isNotEmpty()) {

            var decryptedString: String? = null

            scope.launch(dispatcher) {

                val decrypted = decryptMediaKey(
                    MediaKey(mk)
                )

                @Exhaustive
                when (decrypted) {
                    is Response.Error -> {
                        // Only log it if there is an exception
                        decrypted.exception?.let { nnE ->
                            LOG.e(
                                TAG,
                                """
                            ${decrypted.message}
                            MessageId: $messageId
                            MediaKey: $mediaKey
                            """.trimIndent(),
                                nnE
                            )
                        }
                    }
                    is Response.Success -> {
                        decryptedString = decrypted.value.toUnencryptedString(trim = false).value
                    }
                }

            }

            return decryptedString

        } else {
            null
        }
    }


    @OptIn(UnencryptedDataAccess::class)
    private suspend fun decryptMessageDtoMediaKeyIfAvailable(
        message: MessageDto,
        scope: CoroutineScope,
        dispatcher: CoroutineDispatcher = mainImmediate,
    ): Job? =
        message.media_key?.let { mediaKey ->

            if (mediaKey.isNotEmpty()) {

                scope.launch(dispatcher) {

                    val decrypted = decryptMediaKey(
                        MediaKey(mediaKey)
                    )

                    @Exhaustive
                    when (decrypted) {
                        is Response.Error -> {
                            // Only log it if there is an exception
                            decrypted.exception?.let { nnE ->
                                LOG.e(
                                    TAG,
                                    """
                                    ${decrypted.message}
                                    MessageId: ${message.id}
                                    MediaKey: ${message.media_key}
                                """.trimIndent(),
                                    nnE
                                )
                            }
                        }
                        is Response.Success -> {
                            message.setMediaKeyDecrypted(
                                decrypted.value.toUnencryptedString(trim = false).value
                            )
                        }
                    }

                }

            } else {
                null
            }
        }

    override suspend fun payForInvite(invite: Invite) {
        val queries = coreDB.getSphinxDatabaseQueries()

        contactLock.withLock {
            withContext(io) {
                queries.transaction {
                    updatedContactIds.add(invite.contactId)
                    updateInviteStatus(invite.id, InviteStatus.ProcessingPayment, queries)
                }
            }
        }

        delay(25L)
    }

    override suspend fun deleteInviteAndContact(inviteString: String) {
        val queries = coreDB.getSphinxDatabaseQueries()
        val invite = getInviteByString(InviteString(inviteString)).firstOrNull()
        val inviteCode = invite?.inviteCode?.value

        if (inviteCode != null) {
            withContext(io) {
                queries.inviteDeleteById(invite.id)
                queries.contactDeleteById(invite.contactId)
            }

            connectManager.deleteInvite(inviteCode)
        } else {
            onConnectManagerError(ConnectManagerError.DeleteInviteError)
        }
    }

    override suspend fun authorizeStakwork(
        host: String,
        id: String,
        challenge: String
    ): Response<String, ResponseError> {
        var response: Response<String, ResponseError>? = null

        applicationScope.launch(mainImmediate) {
//            networkQueryMemeServer.signChallenge(
//                AuthenticationChallenge(challenge)
//            ).collect { loadResponse ->
//                when (loadResponse) {
//                    is LoadResponse.Loading -> {
//                    }
//
//                    is Response.Error -> {
//                        response = loadResponse
//                    }
//
//                    is Response.Success -> {
//
//                        val sig = loadResponse.value.sig
//                        val publicKey = accountOwner.value?.nodePubKey?.value ?: ""
//
//                        var urlString = "https://auth.sphinx.chat/oauth_verify?id=$id&sig=$sig&pubkey=$publicKey"
//
//                        accountOwner.value?.routeHint?.value?.let {
//                            urlString += "&route_hint=$it"
//                        }
//
//                        response = Response.Success(urlString)
//                    }
//                }
//            }
        }.join()

        return response ?: Response.Error(ResponseError("Returned before completing"))
    }

    override suspend fun redeemSats(
        host: String,
        token: String
    ): Response<Boolean, ResponseError> {
        var response: Response<Boolean, ResponseError>? = null

        applicationScope.launch(mainImmediate) {
            networkQueryAuthorizeExternal.redeemSats(
                host,
                RedeemSatsDto(
                    accountOwner.value?.getNodeDescriptor(),
                    token
                )
            ).collect { loadResponse ->
                when (loadResponse) {
                    is LoadResponse.Loading -> {}

                    is Response.Error -> {
                        response = loadResponse
                    }

                    is Response.Success -> {
                        response = Response.Success(true)
                    }
                }
            }
        }.join()

        return response ?: Response.Error(ResponseError("Returned before completing"))
    }

    override suspend fun deletePeopleProfile(
        body: String
    ): Response<Boolean, ResponseError> {
        var response: Response<Boolean, ResponseError>? = null

        applicationScope.launch(mainImmediate) {
            moshi.adapter(DeletePeopleProfileDto::class.java).fromJson(body)
                ?.let { deletePeopleProfileDto ->
//                    networkQueryPeople.deletePeopleProfile(
//                        deletePeopleProfileDto
//                    ).collect { loadResponse ->
//                        when (loadResponse) {
//                            is LoadResponse.Loading -> {
//                            }
//                            is Response.Error -> {
//                            }
//                            is Response.Success -> {
//                                response = Response.Success(true)
//                            }
//                        }
//                    }
                }
        }.join()

        return response ?: Response.Error(ResponseError("Profile delete failed"))
    }

    override suspend fun savePeopleProfile(
        body: String
    ): Response<Boolean, ResponseError> {
        var response: Response<Boolean, ResponseError>? = null

        applicationScope.launch(mainImmediate) {
            moshi.adapter(PeopleProfileDto::class.java).fromJson(body)?.let { profile ->
//                networkQueryPeople.savePeopleProfile(
//                    profile
//                ).collect { saveProfileResponse ->
//                    when (saveProfileResponse) {
//                        is LoadResponse.Loading -> {
//                        }
//
//                        is Response.Error -> {
//                            response = saveProfileResponse
//                        }
//
//                        is Response.Success -> {
//                            response = Response.Success(true)
//                        }
//                    }
//                }
            }
        }.join()

        return response ?: Response.Error(ResponseError("Profile save failed"))
    }

    override suspend fun storeTribe(createTribe: CreateTribe, chatId: ChatId?) {
        val memeServerHost = MediaHost.DEFAULT

        applicationScope.launch(mainImmediate) {
            try {
                val imgUrl: String? = createTribe.img?.let { imgFile ->
                    // If an image file is provided we should upload it
                    val token =
                        memeServerTokenHandler.retrieveAuthenticationToken(memeServerHost)
                            ?: throw RuntimeException("MemeServerAuthenticationToken retrieval failure")

                    val networkResponse = networkQueryMemeServer.uploadAttachment(
                        authenticationToken = token,
                        mediaType = MediaType.Image("${MediaType.IMAGE}/${imgFile.extension}"),
                        stream = object : InputStreamProvider() {
                            override fun newInputStream(): InputStream = imgFile.inputStream()
                        },
                        fileName = imgFile.name,
                        contentLength = imgFile.length(),
                        memeServerHost = memeServerHost,
                    )
                    @Exhaustive
                    when (networkResponse) {
                        is Response.Error -> {
                            LOG.e(TAG, "Failed to upload image: ", networkResponse.exception)
                            null
                        }

                        is Response.Success -> {
                            "https://${memeServerHost.value}/public/${networkResponse.value.muid}"
                        }
                    }
                }

                val ownerAlias = accountOwner.value?.alias?.value ?: "unknown"

                if (chatId == null) {
                    val newTribeJson = createTribe.toNewCreateTribe(ownerAlias, imgUrl, null).toJson()
                    connectManager.createTribe(newTribeJson)
                } else {
                    val tribe = getChatById(chatId).firstOrNull()
                    if (tribe != null) {
                        val updatedTribeJson = createTribe.toNewCreateTribe(ownerAlias, imgUrl, tribe.uuid.value).toJson()
                        tribe.ownerPubKey?.value?.let { connectManager.editTribe(updatedTribeJson) }
                    }
                }
            } catch (e: Exception) { }
        }
    }

    override suspend fun updateTribe(
        chatId: ChatId,
        createTribe: CreateTribe
    ) {

        var response: Response<Any, ResponseError> =
            Response.Error(ResponseError(("Failed to exit tribe")))
        val memeServerHost = MediaHost.DEFAULT

        applicationScope.launch(mainImmediate) {
            try {
                val imgUrl: String? = (createTribe.img?.let { imgFile ->
                    val token =
                        memeServerTokenHandler.retrieveAuthenticationToken(memeServerHost)
                            ?: throw RuntimeException("MemeServerAuthenticationToken retrieval failure")

                    val networkResponse = networkQueryMemeServer.uploadAttachment(
                        authenticationToken = token,
                        mediaType = MediaType.Image("${MediaType.IMAGE}/${imgFile.extension}"),
                        stream = object : InputStreamProvider() {
                            override fun newInputStream(): InputStream = imgFile.inputStream()
                        },
                        fileName = imgFile.name,
                        contentLength = imgFile.length(),
                        memeServerHost = memeServerHost,
                    )
                    @Exhaustive
                    when (networkResponse) {
                        is Response.Error -> {
                            LOG.e(TAG, "Failed to upload image: ", networkResponse.exception)
                            response = networkResponse
                            null
                        }
                        is Response.Success -> {
                            "https://${memeServerHost.value}/public/${networkResponse.value.muid}"
                        }
                    }
                }) ?: createTribe.imgUrl

                val tribe = getChatById(chatId).firstOrNull()
                // create the tribe json
                if (tribe != null) {
                    connectManager.editTribe("")
                }

//                val queries = coreDB.getSphinxDatabaseQueries()
//
//                chatLock.withLock {
//                    messageLock.withLock {
//                        withContext(io) {
//                            queries.transaction {
//                                upsertChat(
//                                    loadResponse.value,
//                                    moshi,
//                                    chatSeenMap,
//                                    queries,
//                                    null,
//                                    accountOwner.value?.nodePubKey
//                                )
//                            }
//                        }
//                    }
//                }

            } catch (e: Exception) {
                response = Response.Error(
                    ResponseError("Failed to update Chat Profile", e)
                )
            }
        }
    }

    private suspend fun togglePinMessage(
        chatId: ChatId,
        message: Message,
        isUnpinMessage: Boolean,
        errorMessage: String,
        isProductionEnvironment: Boolean
    ): Response<Any, ResponseError> {
        var response: Response<Any, ResponseError> = Response.Error(ResponseError(errorMessage))

        applicationScope.launch(mainImmediate) {
            val tribe = getChatById(chatId).firstOrNull()
            val host = tribe?.host
            val pubKey = tribe?.uuid?.value?.toLightningNodePubKey()

            if (host != null && pubKey != null) {

                networkQueryChat.getTribeInfo(host, pubKey, isProductionEnvironment).collect { loadResponse ->
                    when (loadResponse) {
                        is LoadResponse.Loading -> {}
                        is Response.Error -> {
                            response = loadResponse
                        }
                        is Response.Success -> {
                            val pinUpdatedTribeInfo = if (isUnpinMessage) {
                                loadResponse.value.copy(pin = null).toJsonString()
                            } else {
                                loadResponse.value.copy(pin = message.uuid?.value).toJsonString()
                            }

                            connectManager.editTribe(pinUpdatedTribeInfo)

                            val queries = coreDB.getSphinxDatabaseQueries()
                            response = Response.Success(loadResponse)

                            chatLock.withLock {
                                messageLock.withLock {
                                    withContext(io) {
                                        queries.chatUpdatePinMessage(
                                            if (isUnpinMessage) null else message.uuid,
                                            chatId
                                        )
                                        queries.messageUpdateStatus(message.status, message.id)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }.join()

        return response
    }

    override suspend fun pinMessage(
        chatId: ChatId,
        message: Message,
        isProductionEnvironment: Boolean
    ): Response<Any, ResponseError> {
        return togglePinMessage(
            chatId,
            message,
            false,
            "Failed to pin message",
            isProductionEnvironment
        )
    }

    override suspend fun unPinMessage(
        chatId: ChatId,
        message: Message,
        isProductionEnvironment: Boolean
    ): Response<Any, ResponseError> {
        return togglePinMessage(
            chatId,
            message,
            true,
            "Failed to unpin message",
            isProductionEnvironment
        )
    }

    override suspend fun processMemberRequest(
        chatId: ChatId,
        messageUuid: MessageUUID?,
        memberPubKey: LightningNodePubKey?,
        type: MessageType.GroupAction,
        alias: SenderAlias?
    ) {
        val messageBuilder = SendMessage.Builder()
        messageBuilder.setChatId(chatId)
        messageBuilder.setGroupAction(type)

        // Accept or Reject member
        messageUuid?.value?.let { nnMessageUuid ->
            messageBuilder.setReplyUUID(ReplyUUID(nnMessageUuid))
        }

        // Kick Member
        memberPubKey?.let { nnContactKey ->
            messageBuilder.setMemberPubKey(nnContactKey)
        }

        alias?.let { senderAlias ->
            messageBuilder.setSenderAlias(senderAlias)
        }

        sendMessage(messageBuilder.build().first)
    }

    override suspend fun addTribeMember(addMember: AddMember): Response<Any, ResponseError> {
        var response: Response<Any, ResponseError> =
            Response.Error(ResponseError(("Failed to add Member")))
        val memeServerHost = MediaHost.DEFAULT

        applicationScope.launch(mainImmediate) {
            try {
                val imgUrl: String? = addMember.img?.let { imgFile ->
                    // If an image file is provided we should upload it
                    val token = memeServerTokenHandler.retrieveAuthenticationToken(memeServerHost)
                        ?: throw RuntimeException("MemeServerAuthenticationToken retrieval failure")

                    val networkResponse = networkQueryMemeServer.uploadAttachment(
                        authenticationToken = token,
                        mediaType = MediaType.Image("${MediaType.IMAGE}/${imgFile.extension}"),
                        stream = object : InputStreamProvider() {
                            override fun newInputStream(): InputStream = imgFile.inputStream()
                        },
                        fileName = imgFile.name,
                        contentLength = imgFile.length(),
                        memeServerHost = memeServerHost,
                    )
                    @Exhaustive
                    when (networkResponse) {
                        is Response.Error -> {
                            LOG.e(TAG, "Failed to upload image: ", networkResponse.exception)
                            response = networkResponse
                            null
                        }
                        is Response.Success -> {
                            "https://${memeServerHost.value}/public/${networkResponse.value.muid}"
                        }
                    }
                }
                // TODO V2 addTribeMember
//                networkQueryChat.addTribeMember(
//                    addMember.toTribeMemberDto(imgUrl)
//                ).collect { loadResponse ->
//                    when (loadResponse) {
//                        is LoadResponse.Loading -> {
//                        }
//
//                        is Response.Error -> {
//                            response = loadResponse
//                            LOG.e(TAG, "Failed to create tribe: ", loadResponse.exception)
//                        }
//                        is Response.Success -> {
//                            response = Response.Success(true)
//                        }
//                    }
//                }
            } catch (e: Exception) {
                response = Response.Error(
                    ResponseError("Failed to add Tribe Member", e)
                )
            }
        }.join()

        return response
    }

    private val lsatDboPresenterMapper: LsatDboPresenterMapper by lazy {
        LsatDboPresenterMapper(dispatchers)
    }


    override suspend fun getLastLsatByIssuer(issuer: LsatIssuer): Flow<Lsat?> = flow {
        val queries = coreDB.getSphinxDatabaseQueries()
        emitAll(
            queries.lsatGetLastActiveByIssuer(issuer)
                .asFlow()
                .mapToOneOrNull(io)
                .map {
                    it?.let { lsatDboPresenterMapper.mapFrom(it) }
                }
                .distinctUntilChanged()
        )
    }


    override suspend fun getLastLsatActive(): Flow<Lsat?> = flow {
        val queries = coreDB.getSphinxDatabaseQueries()
        emitAll(
            queries.lsatGetLastActive()
                .asFlow()
                .mapToOneOrNull(io)
                .map { it?.let { lsatDboPresenterMapper.mapFrom(it) } }
                .distinctUntilChanged()
        )
    }

    override suspend fun getLsatByIdentifier(identifier: LsatIdentifier): Flow<Lsat?> = flow {
        val queries = coreDB.getSphinxDatabaseQueries()
        emitAll(
            queries.lsatGetById(identifier)
                .asFlow()
                .mapToOneOrNull(io)
                .map { it?.let { lsatDboPresenterMapper.mapFrom(it) } }
                .distinctUntilChanged()
        )
    }

    override suspend fun upsertLsat(lsat: Lsat) {
        val queries = coreDB.getSphinxDatabaseQueries()
        queries.transaction {
            upsertLsat(lsat, queries)
        }
    }

    override suspend fun updateLsatStatus(identifier: LsatIdentifier, status: LsatStatus) {
        val queries = coreDB.getSphinxDatabaseQueries()
        queries.lsatUpdateStatus(status, identifier)
    }

    /***
     * Subscriptions
     */

    private val subscriptionLock = Mutex()
    private val subscriptionDboPresenterMapper: SubscriptionDboPresenterMapper by lazy {
        SubscriptionDboPresenterMapper(dispatchers)
    }

//    override fun getActiveSubscriptionByContactId(contactId: ContactId): Flow<Subscription?> =
//        flow {
//            emitAll(
//                coreDB.getSphinxDatabaseQueries()
//                    .subscriptionGetLastActiveByContactId(contactId)
//                    .asFlow()
//                    .mapToOneOrNull(io)
//                    .map { it?.let { subscriptionDboPresenterMapper.mapFrom(it) } }
//                    .distinctUntilChanged()
//            )
//        }

//    override suspend fun createSubscription(
//        amount: Sat,
//        interval: String,
//        contactId: ContactId,
//        chatId: ChatId?,
//        endDate: String?,
//        endNumber: EndNumber?
//    ): Response<Any, ResponseError> {
//        var response: Response<SubscriptionDto, ResponseError>? = null
//
//        applicationScope.launch(mainImmediate) {
//            networkQuerySubscription.postSubscription(
//                PostSubscriptionDto(
//                    amount = amount.value,
//                    contact_id = contactId.value,
//                    chat_id = chatId?.value,
//                    interval = interval,
//                    end_number = endNumber?.value,
//                    end_date = endDate
//                )
//            ).collect { loadResponse ->
//                @Exhaustive
//                when (loadResponse) {
//                    is LoadResponse.Loading -> {
//                    }
//                    is Response.Error -> {
//                        response = loadResponse
//                    }
//                    is Response.Success -> {
//                        response = loadResponse
//                        val queries = coreDB.getSphinxDatabaseQueries()
//
//                        subscriptionLock.withLock {
//                            withContext(io) {
//                                queries.transaction {
//                                    upsertSubscription(
//                                        loadResponse.value,
//                                        queries
//                                    )
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//        }.join()
//
//        return response ?: Response.Error(ResponseError(("Failed to create subscription")))
//    }

//    override suspend fun updateSubscription(
//        id: SubscriptionId,
//        amount: Sat,
//        interval: String,
//        contactId: ContactId,
//        chatId: ChatId?,
//        endDate: String?,
//        endNumber: EndNumber?
//    ): Response<Any, ResponseError> {
//        var response: Response<SubscriptionDto, ResponseError>? = null
//
//        applicationScope.launch(mainImmediate) {
//
//            networkQuerySubscription.putSubscription(
//                id,
//                PutSubscriptionDto(
//                    amount = amount.value,
//                    contact_id = contactId.value,
//                    chat_id = chatId?.value,
//                    interval = interval,
//                    end_number = endNumber?.value,
//                    end_date = endDate
//                )
//            ).collect { loadResponse ->
//                @Exhaustive
//                when (loadResponse) {
//                    is LoadResponse.Loading -> {
//                    }
//                    is Response.Error -> {
//                        response = loadResponse
//                    }
//                    is Response.Success -> {
//                        response = loadResponse
//                        val queries = coreDB.getSphinxDatabaseQueries()
//
//                        subscriptionLock.withLock {
//                            withContext(io) {
//                                queries.transaction {
//                                    upsertSubscription(
//                                        loadResponse.value,
//                                        queries
//                                    )
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//        }.join()
//
//        return response ?: Response.Error(ResponseError(("Failed to update subscription")))
//    }

//    override suspend fun restartSubscription(
//        subscriptionId: SubscriptionId
//    ): Response<Any, ResponseError> {
//        var response: Response<SubscriptionDto, ResponseError>? = null
//
//        applicationScope.launch(mainImmediate) {
//
//            networkQuerySubscription.putRestartSubscription(
//                subscriptionId
//            ).collect { loadResponse ->
//                @Exhaustive
//                when (loadResponse) {
//                    is LoadResponse.Loading -> {
//                    }
//                    is Response.Error -> {
//                        response = loadResponse
//                    }
//                    is Response.Success -> {
//                        response = loadResponse
//                        val queries = coreDB.getSphinxDatabaseQueries()
//
//                        subscriptionLock.withLock {
//                            withContext(io) {
//                                queries.transaction {
//                                    upsertSubscription(
//                                        loadResponse.value,
//                                        queries
//                                    )
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//        }.join()
//
//        return response ?: Response.Error(ResponseError(("Failed to restart subscription")))
//    }

//    override suspend fun pauseSubscription(
//        subscriptionId: SubscriptionId
//    ): Response<Any, ResponseError> {
//        var response: Response<SubscriptionDto, ResponseError>? = null
//
//        applicationScope.launch(mainImmediate) {
//
//            networkQuerySubscription.putPauseSubscription(
//                subscriptionId
//            ).collect { loadResponse ->
//                @Exhaustive
//                when (loadResponse) {
//                    is LoadResponse.Loading -> {
//                    }
//                    is Response.Error -> {
//                        response = loadResponse
//                    }
//                    is Response.Success -> {
//                        response = loadResponse
//                        val queries = coreDB.getSphinxDatabaseQueries()
//
//                        subscriptionLock.withLock {
//                            withContext(io) {
//                                queries.transaction {
//                                    upsertSubscription(
//                                        loadResponse.value,
//                                        queries
//                                    )
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//        }.join()
//
//        return response ?: Response.Error(ResponseError(("Failed to pause subscription")))
//    }

//    override suspend fun deleteSubscription(
//        subscriptionId: SubscriptionId
//    ): Response<Any, ResponseError> {
//        var response: Response<Any, ResponseError>? = null
//
//        applicationScope.launch(mainImmediate) {
//            networkQuerySubscription.deleteSubscription(
//                subscriptionId
//            ).collect { loadResponse ->
//                @Exhaustive
//                when (loadResponse) {
//                    is LoadResponse.Loading -> {
//                    }
//                    is Response.Error -> {
//                        response = loadResponse
//                    }
//                    is Response.Success -> {
//                        response = loadResponse
//                        val queries = coreDB.getSphinxDatabaseQueries()
//
//                        subscriptionLock.withLock {
//                            withContext(io) {
//                                queries.transaction {
//                                    deleteSubscriptionById(subscriptionId, queries)
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//        }.join()
//
//        return response ?: Response.Error(ResponseError(("Failed to delete subscription")))
//    }

    private val downloadMessageMediaLockMap = SynchronizedMap<MessageId, Pair<Int, Mutex>>()
    override fun downloadMediaIfApplicable(
        message: Message,
        sent: Boolean
    ) {
        applicationScope.launch(mainImmediate) {
            val messageId: MessageId = message.id

            val downloadLock: Mutex = downloadMessageMediaLockMap.withLock { map ->
                val localLock: Pair<Int, Mutex>? = map[messageId]

                if (localLock != null) {
                    map[messageId] = Pair(localLock.first + 1, localLock.second)
                    localLock.second
                } else {
                    Pair(1, Mutex()).let { pair ->
                        map[messageId] = pair
                        pair.second
                    }
                }
            }

            downloadLock.withLock {
                val queries = coreDB.getSphinxDatabaseQueries()

                //Getting media data from purchase accepted item if is paid content
                val media = message.retrieveUrlAndMessageMedia()?.second
                val host = media?.host
                val url = media?.url

                val localFile = message.messageMedia?.localFile

                if (
                    message != null &&
                    media != null &&
                    host != null &&
                    url != null &&
                    localFile == null &&
                    !message.status.isDeleted() &&
                    (!message.isPaidPendingMessage || sent)
                ) {

                    memeServerTokenHandler.retrieveAuthenticationToken(host)?.let { token ->
                        memeInputStreamHandler.retrieveMediaInputStream(
                            url.value,
                            token,
                            media.mediaKeyDecrypted,
                        )?.let { streamAndFileName ->

                            mediaCacheHandler.createFile(
                                mediaType = message.messageMedia?.mediaType ?: media.mediaType,
                                extension = streamAndFileName.second?.getExtension()
                            )?.let { streamToFile ->

                                streamAndFileName.first?.let { stream ->
                                    mediaCacheHandler.copyTo(stream, streamToFile)
                                    messageLock.withLock {
                                        withContext(io) {
                                            queries.transaction {

                                                queries.messageMediaUpdateFile(
                                                    streamToFile,
                                                    streamAndFileName.second,
                                                    messageId
                                                )

                                                // to proc table change so new file path is pushed to UI
                                                queries.messageUpdateContentDecrypted(
                                                    message.messageContentDecrypted,
                                                    messageId
                                                )
                                            }
                                        }
                                    }

                                    // hold downloadLock until table change propagates to UI
                                    delay(200L)

                                }
                            }
                        }
                    }
                }

                // remove lock from map if only subscriber
                downloadMessageMediaLockMap.withLock { map ->
                    map[messageId]?.let { pair ->
                        if (pair.first <= 1) {
                            map.remove(messageId)
                        } else {
                            map[messageId] = Pair(pair.first - 1, pair.second)
                        }
                    }
                }
            }
        }
    }

    private val feedItemLock = Mutex()
    private val downloadFeedItemLockMap = SynchronizedMap<FeedId, Pair<Int, Mutex>>()

    override fun inProgressDownloadIds(): List<FeedId> {
        return downloadFeedItemLockMap.withLock { map ->
            map.keys.toList()
        }
    }

    private var deleteExcess: Job? = null
    override suspend fun deleteExcessFilesOnBackground(excessSize: Long) {
        if (deleteExcess?.isActive == true || excessSize <= 0L) {
            return
        }

        combine(
            getAllDownloadedMedia(),
            getAllDownloadedFeedItems())
        { chatFiles, feedFiles ->

            val messages: List<Message?>? = getMessagesByIds(chatFiles.map { it.messageId }).firstOrNull()
            val combinedFileList = mutableListOf<Triple<Any, File, DateTime>>()

            messages?.forEach { nnMessages ->
                nnMessages?.let { message ->
                    val messageMedia = chatFiles.firstOrNull() { it.messageId == message.id  }
                    val localFile: File? = messageMedia?.localFile
                    val date = message.date

                    if (localFile != null) {
                        combinedFileList.add(Triple(messageMedia, localFile, date))
                    }
                }
            }

            feedFiles.forEach { feedItem ->
                feedItem.let {
                    val localFile = it.localFile
                    val datePublished = it.datePublished

                    if (localFile != null && datePublished != null) {
                        combinedFileList.add(Triple(feedItem, localFile, datePublished))
                    }
                }
            }

            combinedFileList.sortBy { it.third.value }

            val filesToDelete = mutableListOf<Triple<Any, File, DateTime>>()
            var totalSize = 0L

            for (item in combinedFileList) {
                val fileSize = item.second.length()

                if (totalSize < excessSize) {
                    totalSize += fileSize

                    filesToDelete.add(item)
                }
            }

            val (messageMedias, feedItems) = filesToDelete.partition { it.first is MessageMedia }

            val messageMediaTriples: List<Triple<ChatId, List<File>, List<MessageId>>> =

                messageMedias.map {
                    val messageMedia = it.first as MessageMedia
                    val file = it.second
                    Pair(messageMedia, file)
                }.let{ messageMediaFiles ->

                    messageMediaFiles.groupBy { it.first.chatId }.map { (chatId, list) ->
                        Triple(chatId, list.map { it.second }, list.map { it.first.messageId })
                    }
                }

            val feedItemFiles: List<FeedItem> = feedItems.map { it.first as FeedItem }

            deleteExcess = CoroutineScope(dispatchers.io).launch {

                feedItemFiles.forEach { feedItem ->
                    deleteDownloadedMediaIfApplicable(feedItem)
                }

                messageMediaTriples.forEach { tripe ->
                    deleteDownloadedMediaByChatId(tripe.first, tripe.second, tripe.third )
                }
            }

        }.first()
    }

    override fun downloadMediaIfApplicable(
        feedItem: DownloadableFeedItem,
        downloadCompleteCallback: (downloadedFile: File) -> Unit
    ) {
        val feedItemId: FeedId = feedItem.id

        val downloadLock: Mutex = downloadFeedItemLockMap.withLock { map ->
            val localLock: Pair<Int, Mutex>? = map[feedItemId]

            if (localLock != null) {
                map[feedItemId] = Pair(localLock.first + 1, localLock.second)
                localLock.second
            } else {
                Pair(1, Mutex()).let { pair ->
                    map[feedItemId] = pair
                    pair.second
                }
            }
        }

        applicationScope.launch(mainImmediate) {
            downloadLock.withLock {
                sphinxNotificationManager.notify(
                    notificationId = SphinxNotificationManager.DOWNLOAD_NOTIFICATION_ID,
                    title = "Downloading Item",
                    message = "Downloading item for local playback",
                )

                val queries = coreDB.getSphinxDatabaseQueries()

                val url = feedItem.enclosureUrl.value
                val contentType = feedItem.enclosureType
                val localFile = feedItem.localFile

                if (
                    contentType != null &&
                    localFile == null
                ) {
                    val streamToFile: File? = mediaCacheHandler.createFile(
                        contentType.value.toMediaType()
                    )

                    if (streamToFile != null) {
                        memeInputStreamHandler.retrieveMediaInputStream(
                            url,
                            authenticationToken = null,
                            mediaKeyDecrypted = null,
                        )?.let { streamAndFileName ->
                            streamAndFileName.first?.let { stream ->
                                sphinxNotificationManager.notify(
                                    notificationId = SphinxNotificationManager.DOWNLOAD_NOTIFICATION_ID,
                                    title = "Completing Download",
                                    message = "Finishing up download of file",
                                )
                                mediaCacheHandler.copyTo(stream, streamToFile)

                                feedItemLock.withLock {
                                    withContext(io) {
                                        queries.transaction {
                                            queries.feedItemUpdateLocalFile(
                                                streamToFile,
                                                feedItemId
                                            )
                                        }
                                    }
                                }

                                sphinxNotificationManager.notify(
                                    notificationId = SphinxNotificationManager.DOWNLOAD_NOTIFICATION_ID,
                                    title = "Download complete",
                                    message = "item can now be accessed offline",
                                )
                                // hold downloadLock until table change propagates to UI
                                delay(200L)
                                downloadCompleteCallback.invoke(streamToFile)

                            } ?: streamToFile.delete()

                        } ?: streamToFile.delete()
                    }
                } else {
                    val title = if (localFile != null) {
                        "Item already downloaded"
                    } else {
                        "Failed to initiate download"
                    }
                    val message = if (localFile != null) {
                        "You have already downloaded this item."
                    } else {
                        "Failed to initiate download because of missing media type information"
                    }
                    sphinxNotificationManager.notify(
                        notificationId = SphinxNotificationManager.DOWNLOAD_NOTIFICATION_ID,
                        title = title,
                        message = message,
                    )
                }

                // remove lock from map if only subscriber
                downloadFeedItemLockMap.withLock { map ->
                    map[feedItemId]?.let { pair ->
                        if (pair.first <= 1) {
                            map.remove(feedItemId)
                        } else {
                            map[feedItemId] = Pair(pair.first - 1, pair.second)
                        }
                    }
                }
            }
        }
    }

    override suspend fun getStorageDataInfo(): Flow<StorageData> =
        combine(
            getAllDownloadedMedia(),
            getAllDownloadedFeedItems())
        { chatFiles, feedFiles ->

            var imagesSize: Long = 0L
            var videoSize: Long = 0L
            var audioSize: Long = 0L
            var filesSize: Long = 0L

            val chat: Long = chatFiles.sumOf { it.localFile?.length() ?: 0L }
            val podcast: Long = feedFiles.sumOf { it.localFile?.length() ?: 0L }

            val imageFiles = mutableListOf<File>()
            val videoFiles = mutableListOf<File>()
            val audioFiles = mutableListOf<File>()
            val otherFiles = mutableListOf<File>()

            val imageItems = mutableMapOf<ChatId, List<MessageId>>()
            val videoItems = mutableMapOf<ChatId, List<MessageId>>()
            val audioItems = mutableMapOf<ChatId, List<MessageId>>()
            val otherItems = mutableMapOf<ChatId, List<MessageId>>()

            chatFiles.forEach { messageMedia ->
                messageMedia.localFile?.let { file ->
                    when {
                        messageMedia.mediaType.isImage -> {
                            imagesSize += file.length()
                            imageFiles.add(file)
                            imageItems[messageMedia.chatId] = imageItems[messageMedia.chatId]?.plus(messageMedia.messageId) ?: listOf(messageMedia.messageId)
                        }
                        messageMedia.mediaType.isVideo -> {
                            videoSize += file.length()
                            videoFiles.add(file)
                            videoItems[messageMedia.chatId] = videoItems[messageMedia.chatId]?.plus(messageMedia.messageId) ?: listOf(messageMedia.messageId)
                        }
                        messageMedia.mediaType.isAudio -> {
                            audioSize += file.length()
                            audioFiles.add(file)
                            audioItems[messageMedia.chatId] = audioItems[messageMedia.chatId]?.plus(messageMedia.messageId) ?: listOf(messageMedia.messageId)
                        }
                        else -> {
                            filesSize += file.length()
                            otherFiles.add(file)
                            otherItems[messageMedia.chatId] = otherItems[messageMedia.chatId]?.plus(messageMedia.messageId) ?: listOf(messageMedia.messageId)
                        }
                    }
                }
            }

            feedFiles.forEach { feedItem ->
                feedItem.localFile?.let { file ->
                    audioSize += file.length()
                    audioFiles.add(file)
                }
            }

            val usedStorage = chat + podcast

            val storageData = StorageData(
                usedStorage = FileSize(usedStorage),
                null,
                chatsStorage = FileSize(chat),
                podcastsStorage = FileSize(podcast),
                images = ImageStorage(FileSize(imagesSize), imageFiles, imageItems),
                video = VideoStorage(FileSize(videoSize), videoFiles, videoItems),
                audio = AudioStorage(FileSize(audioSize), audioFiles, audioItems, feedFiles.distinctBy { it.feedId }.map { it.feedId }),
                files = FilesStorage(FileSize(filesSize), otherFiles, otherItems)
            )

            storageData
        }

    override fun getAllMessageMediaByChatId(chatId: ChatId): Flow<List<MessageMedia>> =
        flow {
        val queries = coreDB.getSphinxDatabaseQueries()

            val messageMediaList = queries.messageMediaGetByChatId(chatId).executeAsList()
            val messageMedia = messageMediaList.map { messageMediaDbo ->
                MessageMediaDboWrapper(messageMediaDbo)
            }
            emit(messageMedia)
        }

    override fun getAllDownloadedMedia(): Flow<List<MessageMedia>> =
        flow {
            emitAll(
                coreDB.getSphinxDatabaseQueries().messageMediaGetAllDownloaded(::MessageMediaDbo)
                    .asFlow()
                    .mapToList(io)
                    .map { listMessageMediaDbo ->
                        listMessageMediaDbo.map { messageMediaDbo ->
                            MessageMediaDboWrapper(messageMediaDbo)
                        }
                    }
                    .distinctUntilChanged()
            )
        }

    override fun getAllDownloadedMediaByChatId(chatId: ChatId): Flow<List<MessageMedia>> =
        flow {
            emitAll(
                coreDB.getSphinxDatabaseQueries().messageMediaGetAllDownloadedByChatId(chatId, ::MessageMediaDbo)
                .asFlow()
                .mapToList(io)
                .map {  listMessageMediaDbo ->
                    listMessageMediaDbo.map { messageMediaDbo ->
                        MessageMediaDboWrapper(messageMediaDbo)
                    }
                }
                .distinctUntilChanged()
            )
        }

    override suspend fun deleteDownloadedMediaByChatId(chatId: ChatId, files: List<File>, messageIds: List<MessageId>?): Boolean {
        return withContext(mainImmediate) {
            val queries = coreDB.getSphinxDatabaseQueries()
            try {
                files.forEach { localFile ->
                    if (localFile.exists()) {
                        localFile.delete()
                    }
                }
                if (messageIds != null) {
                    feedItemLock.withLock {
                        withContext(io) {
                            queries.transaction {
                                queries.messageMediaDeleteMediaById(chatId, messageIds)
                            }
                        }
                    }
                    delay(200L)
                    true
                }
                else {
                    feedItemLock.withLock {
                        withContext(io) {
                            queries.transaction {
                                queries.messageMediaDeleteAllMediaByChatId(chatId)
                            }
                        }
                    }
                    delay(200L)
                    true
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    override suspend fun deleteDownloadedMediaIfApplicable(
        feedItem: DownloadableFeedItem
    ): Boolean {
        val feedItemId: FeedId = feedItem.id
        val queries = coreDB.getSphinxDatabaseQueries()

        val localFile = feedItem.localFile

        localFile?.let {
            try {
                if (it.exists()) {
                    it.delete()
                }

                feedItemLock.withLock {
                    withContext(io) {
                        queries.transaction {
                            queries.feedItemUpdateLocalFile(
                                null,
                                feedItemId
                            )
                        }
                    }
                }
                delay(200L)

                return true
            } catch (e: Exception) {

            }
        }
        return false
    }

    override suspend fun deleteListOfDownloadedMediaIfApplicable(feedItems: List<DownloadableFeedItem>
    ): Boolean {
        val queries = coreDB.getSphinxDatabaseQueries()

        val feedItemsId = feedItems.map { it.id }
        val localFileList = feedItems.mapNotNull { it.localFile }

        localFileList.forEach {
            try {
                if (it.exists()) {
                    it.delete()
                }
            } catch (e: Exception) {
                return false
            }
        }
        feedItemLock.withLock {
            withContext(io) {
                queries.transaction {
                    queries.feedItemUpdateLocalFileByIds(null, feedItemsId)
                }
            }
        }
        delay(200L)

        return true
    }

    override suspend fun deleteAllFeedDownloadedMedia(feed: Feed): Boolean {
        val feedId: FeedId = feed.id
        val queries = coreDB.getSphinxDatabaseQueries()

        val localFileList = feed.items.filter { it.downloaded }

        localFileList.forEach { feedItem ->
            val localFile = feedItem.localFile
            localFile?.let {
                try {
                    if (it.exists()) {
                        it.delete()
                    }
                } catch (e: Exception) {
                    return false
                }
            }
        }
        feedItemLock.withLock {
            withContext(io) {
                queries.transaction {
                    queries.feedItemDeleteAllDownloadedByFeedId(feedId)
                }
            }
        }
        delay(200L)

        return true
    }

    override suspend fun getPaymentTemplates(): Response<List<PaymentTemplate>, ResponseError> {
        var response: Response<List<PaymentTemplate>, ResponseError>? = null

        val memeServerHost = MediaHost.DEFAULT

        memeServerTokenHandler.retrieveAuthenticationToken(memeServerHost)?.let { token ->
            networkQueryMemeServer.getPaymentTemplates(token, moshi = moshi)
                .collect { loadResponse ->
                    @Exhaustive
                    when (loadResponse) {
                        is LoadResponse.Loading -> {
                        }

                        is Response.Error -> {
                            response = loadResponse
                        }

                        is Response.Success -> {
                            var templates = ArrayList<PaymentTemplate>(loadResponse.value.size)

                            for (ptDto in loadResponse.value) {
                                templates.add(
                                    PaymentTemplate(
                                        ptDto.muid,
                                        ptDto.width,
                                        ptDto.height,
                                        token.value
                                    )
                                )
                            }

                            response = Response.Success(templates)
                        }
                    }
                }
        }

        return response ?: Response.Error(ResponseError(("Failed to load payment templates")))
    }

    private val actionTrackDboMessagePresenterMapper: ActionTrackDboMessagePresenterMapper by lazy {
        ActionTrackDboMessagePresenterMapper(dispatchers, moshi)
    }

    private val actionTrackDboFeedSearchPresenterMapper: ActionTrackDboFeedSearchPresenterMapper by lazy {
        ActionTrackDboFeedSearchPresenterMapper(dispatchers, moshi)
    }

    private val actionTrackDboContentBoostPresenterMapper: ActionTrackDboContentBoostPresenterMapper by lazy {
        ActionTrackDboContentBoostPresenterMapper(dispatchers, moshi)
    }

    private val actionTrackDboPodcastClipCommentPresenterMapper: ActionTrackDboPodcastClipCommentPresenterMapper by lazy {
        ActionTrackDboPodcastClipCommentPresenterMapper(dispatchers, moshi)
    }

    private val actionTrackDboContentConsumedPresenterMapper: ActionTrackDboContentConsumedPresenterMapper by lazy {
        ActionTrackDboContentConsumedPresenterMapper(dispatchers, moshi)
    }

    @Suppress("RemoveExplicitTypeArguments")
    override val recommendationsToggleStateFlow: MutableStateFlow<Boolean> by lazy {
        MutableStateFlow<Boolean>(false)
    }

    override fun setRecommendationsToggle(enabled: Boolean) {
        recommendationsToggleStateFlow.value = enabled
    }

    override fun trackFeedSearchAction(searchTerm: String) {
        if (!recommendationsToggleStateFlow.value) {
            return
        }
        applicationScope.launch(io) {
            val queries = coreDB.getSphinxDatabaseQueries()

            val searchTermCount = queries.feedSearchGetCount(
                "%\"searchTerm\":\"$searchTerm\"%"
            ).executeAsOneOrNull() ?: 0

            val feedSearchAction = FeedSearchAction(
                searchTermCount + 1,
                searchTerm,
                Date().time
            )

            queries.actionTrackUpsert(
                ActionTrackType.FeedSearch,
                ActionTrackMetaData(feedSearchAction.toJson(moshi)),
                false.toActionTrackUploaded(),
                ActionTrackId(Long.MAX_VALUE)
            )
        }
    }

    override fun trackFeedBoostAction(
        boost: Long,
        feedItemId: FeedId,
        topics: ArrayList<String>
    ) {
        if (!recommendationsToggleStateFlow.value) {
            return
        }
        applicationScope.launch(io) {
            val queries = coreDB.getSphinxDatabaseQueries()

            getFeedItemById(feedItemId).firstOrNull()?.let { feedItem ->
                getFeedById(feedItem.feedId).firstOrNull()?.let { feed ->
                    feedItem.feed = feed

                    val contentBoostAction = ContentBoostAction(
                        boost,
                        feed.id.value,
                        feed.feedType.value.toLong(),
                        feed.feedUrl.value,
                        feedItem.id.value,
                        feedItem.enclosureUrl.value,
                        feed.titleToShow,
                        feedItem.titleToShow,
                        feedItem.descriptionToShow,
                        topics,
                        feedItem.people,
                        feedItem.datePublishedTime,
                        Date().time
                    )

                    queries.actionTrackUpsert(
                        ActionTrackType.ContentBoost,
                        ActionTrackMetaData(contentBoostAction.toJson(moshi)),
                        false.toActionTrackUploaded(),
                        ActionTrackId(Long.MAX_VALUE)
                    )
                }
            }
        }
    }

    override fun trackPodcastClipComments(
        feedItemId: FeedId,
        timestamp: Long,
        topics: ArrayList<String>
    ) {
        if (!recommendationsToggleStateFlow.value) {
            return
        }
        applicationScope.launch(io) {
            val queries = coreDB.getSphinxDatabaseQueries()

            getFeedItemById(feedItemId).firstOrNull()?.let { feedItem ->
                getFeedById(feedItem.feedId).firstOrNull()?.let { feed ->
                    feedItem.feed = feed

                    val podcastClipCommentAction = PodcastClipCommentAction(
                        feed.id.value,
                        feed.feedType.value.toLong(),
                        feed.feedUrl.value,
                        feedItem.id.value,
                        feedItem.enclosureUrl.value,
                        feed.titleToShow,
                        feedItem.titleToShow,
                        feedItem.descriptionToShow,
                        topics,
                        feedItem.people,
                        feedItem.datePublishedTime,
                        timestamp,
                        timestamp,
                        Date().time
                    )

                    queries.actionTrackUpsert(
                        ActionTrackType.PodcastClipComment,
                        ActionTrackMetaData(podcastClipCommentAction.toJson(moshi)),
                        false.toActionTrackUploaded(),
                        ActionTrackId(Long.MAX_VALUE)
                    )
                }
            }
        }
    }

    override fun trackNewsletterConsumed(feedItemId: FeedId) {
        if (!recommendationsToggleStateFlow.value) {
            return
        }
        applicationScope.launch(io) {
            val queries = coreDB.getSphinxDatabaseQueries()

            getFeedItemById(feedItemId).firstOrNull()?.let { feedItem ->
                getFeedById(feedItem.feedId).firstOrNull()?.let { feed ->
                    feedItem.feed = feed

                    val newsletterConsumedAction = ContentConsumedAction(
                        feed.id.value,
                        feed.feedType.value.toLong(),
                        feed.feedUrl.value,
                        feedItem.id.value,
                        feedItem.enclosureUrl.value,
                        feed.titleToShow,
                        feedItem.titleToShow,
                        feedItem.descriptionToShow,
                        0,
                        arrayListOf(),
                        feedItem.people,
                        feedItem.datePublishedTime
                    )

                    val contentConsumedHistoryItem = ContentConsumedHistoryItem(
                        arrayListOf(""),
                        0,
                        0,
                        Date().time
                    )
                    newsletterConsumedAction.addHistoryItem(contentConsumedHistoryItem)

                    queries.actionTrackUpsert(
                        ActionTrackType.ContentConsumed,
                        ActionTrackMetaData(newsletterConsumedAction.toJson(moshi)),
                        false.toActionTrackUploaded(),
                        ActionTrackId(Long.MAX_VALUE)
                    )
                }
            }
        }
    }

    override fun trackMediaContentConsumed(
        feedItemId: FeedId,
        history: ArrayList<ContentConsumedHistoryItem>
    ) {
        if (!recommendationsToggleStateFlow.value) {
            return
        }
        applicationScope.launch(io) {
            val queries = coreDB.getSphinxDatabaseQueries()

            getFeedItemById(feedItemId).firstOrNull()?.let { feedItem ->
                getFeedById(feedItem.feedId).firstOrNull()?.let { feed ->
                    feedItem.feed = feed

                    val contentConsumedAction = ContentConsumedAction(
                        feed.id.value,
                        feed.feedType.value.toLong(),
                        feed.feedUrl.value,
                        feedItem.id.value,
                        feedItem.enclosureUrl.value,
                        feed.titleToShow,
                        feedItem.titleToShow,
                        feedItem.descriptionToShow,
                        0,
                        arrayListOf(),
                        feedItem.people,
                        feedItem.datePublishedTime
                    )

                    contentConsumedAction.history = ArrayList(
                        history.filter {
                            (it.endTimestamp - it.startTimestamp) > 2000.toLong()
                        }
                    )

                    if (contentConsumedAction.history.isEmpty()) {
                        return@launch
                    }

                    queries.actionTrackUpsert(
                        ActionTrackType.ContentConsumed,
                        ActionTrackMetaData(contentConsumedAction.toJson(moshi)),
                        false.toActionTrackUploaded(),
                        ActionTrackId(Long.MAX_VALUE)
                    )
                }
            }
        }
    }

    override fun trackRecommendationsConsumed(
        feedItemId: FeedId,
        history: ArrayList<ContentConsumedHistoryItem>
    ) {
        if (!recommendationsToggleStateFlow.value) {
            return
        }
        applicationScope.launch(io) {
            val queries = coreDB.getSphinxDatabaseQueries()

            recommendationsPodcast.value?.let { recommendationsPodcast ->
                recommendationsPodcast.getEpisodeWithId(feedItemId.value)?.let { recommendation ->
                    val clipRank = recommendationsPodcast.getItemRankForEpisodeWithId(feedItemId.value).toLong()

                    val contentConsumedAction = ContentConsumedAction(
                        recommendationsPodcast.id.value,
                        recommendation.longType,
                        recommendationsPodcast.feedUrl.value,
                        recommendation.id.value,
                        recommendation.enclosureUrl.value,
                        recommendation.showTitleToShow,
                        recommendation.titleToShow,
                        recommendation.descriptionToShow,
                        clipRank,
                        ArrayList(recommendation.topics),
                        ArrayList(recommendation.people),
                        recommendation.datePublishedTime
                    )

                    contentConsumedAction.history = ArrayList(
                        history.filter {
                            (it.endTimestamp - it.startTimestamp) > 2000.toLong()
                        }
                    )

                    if (contentConsumedAction.history.isEmpty()) {
                        return@launch
                    }

                    queries.actionTrackUpsert(
                        ActionTrackType.ContentConsumed,
                        ActionTrackMetaData(contentConsumedAction.toJson(moshi)),
                        false.toActionTrackUploaded(),
                        ActionTrackId(Long.MAX_VALUE)
                    )
                }
            }
        }
    }

    override fun trackMessageContent(keywords: List<String>) {
        if (!recommendationsToggleStateFlow.value) {
            return
        }
        applicationScope.launch(io) {
            val queries = coreDB.getSphinxDatabaseQueries()

            val messageAction = MessageAction(
                ArrayList(keywords),
                Date().time
            )

            queries.actionTrackUpsert(
                ActionTrackType.Message,
                ActionTrackMetaData(messageAction.toJson(moshi)),
                false.toActionTrackUploaded(),
                ActionTrackId(Long.MAX_VALUE)
            )
        }
    }

    override fun syncActions() {
        if (!recommendationsToggleStateFlow.value) {
            return
        }
        applicationScope.launch(io) {
            val queries = coreDB.getSphinxDatabaseQueries()

            val actionsDboList = queries.actionTrackGetAllNotUploaded()
                .executeAsList()

//            for (chunk in actionsDboList.chunked(50)) {
//                val actionsIds = chunk.map { it.id }
//
//                val actionTrackDTOs: MutableList<ActionTrackDto> = mutableListOf()
//
//                chunk.forEach {
//                    it.meta_data.value.toActionTrackMetaDataDtoOrNull(moshi)?.let { metaDataDto ->
//                        actionTrackDTOs.add(
//                            ActionTrackDto(
//                                it.type.value,
//                                metaDataDto
//                            )
//                        )
//                    }
//                }
//
//                networkQueryActionTrack.sendActionsTracked(
//                    SyncActionsDto(actionTrackDTOs)
//                ).collect { response ->
//                    when (response) {
//                        is Response.Success -> {
//                            queries.actionTrackUpdateUploadedItems(actionsIds)
//                        }
//                        is Response.Error -> {}
//                        else -> {}
//                    }
//                }
//            }
        }
    }

    override fun updateContentFeedStatus(
        feedId: FeedId,
        itemId: FeedId
    ) {
        applicationScope.launch(io) {
            val queries = coreDB.getSphinxDatabaseQueries()

            contentFeedLock.withLock {
                queries.contentFeedStatusUpdateItemId(
                    itemId,
                    feedId
                )
            }
        }
    }

    private val contentFeedLock = Mutex()
    override fun updateContentFeedStatus(
        feedId: FeedId,
        feedUrl: FeedUrl,
        subscriptionStatus: Subscribed,
        chatId: ChatId?,
        itemId: FeedId?,
        satsPerMinute: Sat?,
        playerSpeed: FeedPlayerSpeed?,
        shouldSync: Boolean
    ) {
        applicationScope.launch(io) {
            val queries = coreDB.getSphinxDatabaseQueries()

            if (feedId.value == FeedRecommendation.RECOMMENDATION_PODCAST_ID) {
                return@launch
            }

            contentFeedLock.withLock {
                queries.contentFeedStatusUpsert(
                    feed_id = feedId,
                    feed_url = feedUrl,
                    subscription_status = subscriptionStatus,
                    chat_id = if (chatId?.value == ChatId.NULL_CHAT_ID.toLong()) null else chatId,
                    item_id = if (itemId?.value == FeedId.NULL_FEED_ID) null else itemId,
                    sats_per_minute = satsPerMinute,
                    player_speed = playerSpeed
                )
            }

            if (shouldSync) {
                saveContentFeedStatusFor(feedId)
            }
        }
    }
    private val contentEpisodeLock = Mutex()
    override fun updateContentEpisodeStatus(
        feedId: FeedId,
        itemId: FeedId,
        duration: FeedItemDuration,
        currentTime: FeedItemDuration,
        played: Boolean,
        shouldSync: Boolean
    ) {
        applicationScope.launch(io) {
            val queries = coreDB.getSphinxDatabaseQueries()

            if (feedId.value == FeedRecommendation.RECOMMENDATION_PODCAST_ID) {
                return@launch
            }

            contentEpisodeLock.withLock {
                queries.contentEpisodeStatusUpsert(
                    feed_id = feedId,
                    item_id = itemId,
                    duration = duration,
                    current_time = currentTime,
                    played = played

                )
            }

            if (shouldSync) {
                saveContentFeedStatusFor(feedId)
            }
        }
    }

    private fun updateContentEpisodeStatusDuration(
        itemId: FeedId,
        feedId: FeedId,
        duration: FeedItemDuration,
        queries: SphinxDatabaseQueries,
        played: Boolean = false
    ) {
        applicationScope.launch(io) {
            contentEpisodeLock.withLock {
                queries.contentEpisodeStatusUpsert(
                    duration,
                    FeedItemDuration(0),
                    itemId,
                    feedId,
                    played
                )
            }
        }
    }

    override fun updatePlayedMark(
        feedItemId: FeedId,
        played: Boolean
    ) {
        applicationScope.launch(io) {
            val queries = coreDB.getSphinxDatabaseQueries()

            contentEpisodeLock.withLock {
                queries.contentEpisodeStatusUpdatePlayed(
                    played,
                    feedItemId
                )
            }
        }
    }

    override fun updateLastPlayed(feedId: FeedId) {
        applicationScope.launch(io) {
            val queries = coreDB.getSphinxDatabaseQueries()

            contentFeedLock.withLock {
                queries.feedUpdateLastPlayed(
                    DateTime.nowUTC().toDateTime(),
                    feedId
                )
            }
        }
    }

    override fun getPlayedMark(feedItemId: FeedId): Flow<Boolean?> = flow {
        emitAll(
            coreDB.getSphinxDatabaseQueries().contentEpisodeStatusGetPlayedByItemId(feedItemId)
                .asFlow()
                .mapToOneOrNull()
                .map { it?.played }
                .distinctUntilChanged()
        )
    }


    override fun saveContentFeedStatuses() {
        applicationScope.launch(io) {

            val contentFeedStatuses: MutableList<ContentFeedStatusDto> = mutableListOf()

            getAllSubscribedFeeds().firstOrNull()?.let { feeds ->
                for (feed in feeds) {
                    getContentFeedStatusDtoFrom(feed)?.let { feedStatus ->
                        contentFeedStatuses.add(feedStatus)
                    }
                }
            }

            if (contentFeedStatuses.isEmpty()) {
                return@launch
            }
            // TODO V2 saveFeedStatuses
//            networkQueryFeedStatus.saveFeedStatuses(
//                PostFeedStatusDto(contentFeedStatuses)
//            ).collect { loadResponse ->
//                @Exhaustive
//                when (loadResponse) {
//                    is LoadResponse.Loading -> {}
//                    is Response.Error -> {}
//                    is Response.Success -> {}
//                }
//            }
        }
    }

    private fun saveContentFeedStatusFor(feedId: FeedId) {
        applicationScope.launch(io) {

            var contentFeedStatus: ContentFeedStatusDto? = null

            getFeedById(feedId).firstOrNull()?.let { feed ->
                contentFeedStatus = getContentFeedStatusDtoFrom(feed)
            }
            // TODO V2 saveFeedStatus
//            contentFeedStatus?.let { feedStatus ->
//                networkQueryFeedStatus.saveFeedStatus(
//                    feedId,
//                    PutFeedStatusDto(feedStatus)
//                ).collect { }
//            }
        }
    }

    private fun getContentFeedStatusDtoFrom(feed: Feed) : ContentFeedStatusDto? {
        var contentFeedStatusDto: ContentFeedStatusDto?
        val nnContentFeedStatus = feed.getNNContentFeedStatus()

        val episodeStatuses : MutableList<Map<String, EpisodeStatusDto>> = mutableListOf()

        if (feed.isPodcast) {
            for (feedItem in feed.items) {
                feedItem.contentEpisodeStatus?.let { episodeStatus ->
                    if (episodeStatus.currentTime.value > 0.toLong() || episodeStatus.duration.value > 0.toLong()) {
                        val status: MutableMap<String, EpisodeStatusDto> = mutableMapOf()

                        status[feedItem.id.value] = EpisodeStatusDto(
                            episodeStatus.duration.value,
                            episodeStatus.currentTime.value
                        )

                        episodeStatuses.add(status)
                    }
                }
            }
        }

        nnContentFeedStatus.let { feedStatus ->
            contentFeedStatusDto = ContentFeedStatusDto(
                feedStatus.feedId.value,
                feedStatus.feedUrl.value,
                feedStatus.subscriptionStatus.isTrue(),
                feedStatus.actualChatId?.value,
                feedStatus.itemId?.value,
                feedStatus.satsPerMinute?.value,
                feedStatus.playerSpeed?.value,
                episodeStatuses
            )
        }

        return contentFeedStatusDto
    }

    override fun restoreContentFeedStatuses(
        playingPodcastId: String?,
        playingEpisodeId: String?,
        durationRetrieverHandler: ((url: String) -> Long)?
    ) {
        // TODO V2 getFeedStatuses
//        applicationScope.launch(io) {
//            networkQueryFeedStatus.getFeedStatuses().collect { loadResponse ->
//                @Exhaustive
//                when (loadResponse) {
//                    is LoadResponse.Loading -> {}
//                    is Response.Error -> {}
//                    is Response.Success -> {
//                        restoreContentFeedStatusesFrom(
//                            loadResponse.value,
//                            playingPodcastId,
//                            playingEpisodeId,
//                            durationRetrieverHandler
//                        )
//                    }
//                }
//            }
//        }
    }

    override fun restoreContentFeedStatusByFeedId(
        feedId: FeedId,
        playingPodcastId: String?,
        playingEpisodeId: String?
    ) {
        // TODO V2 getByFeedId
//       applicationScope.launch(io) {
//           networkQueryFeedStatus.getByFeedId(feedId).collect { loadResponse ->
//               @Exhaustive
//               when (loadResponse) {
//                   is LoadResponse.Loading -> {}
//                   is Response.Error -> {}
//                   is Response.Success -> {
//                       restoreContentFeedStatusFrom(
//                           loadResponse.value,
//                           null,
//                           playingPodcastId,
//                           playingEpisodeId
//                       )
//                   }
//               }
//           }
//       }
    }

    private suspend fun restoreContentFeedStatusesFrom(
        contentFeedStatuses: List<ContentFeedStatusDto>,
        playingPodcastId: String?,
        playingEpisodeId: String?,
        durationRetrieverHandler: ((url: String) -> Long)? = null
    ) {
        if (contentFeedStatuses.isEmpty()) {
            return
        }

        applicationScope.launch(io) {
            val queries = coreDB.getSphinxDatabaseQueries()

            queries.feedBatchUnsubscribe(contentFeedStatuses.map { FeedId(it.feed_id) })

            for (contentFeedStatus in contentFeedStatuses) {
                restoreContentFeedStatusFrom(
                    contentFeedStatus,
                    queries,
                    playingPodcastId,
                    playingEpisodeId
                )
            }
        }.join()

        fetchFeedNewItems(durationRetrieverHandler)
    }

    private fun fetchFeedNewItems(
        durationRetrieverHandler: ((url: String) -> Long)? = null
    ) {
        applicationScope.launch(io) {
            getAllSubscribedFeeds().firstOrNull()?.let { feeds ->
                for (feed in feeds) {
                    updateFeedContentItemsFor(
                        feed,
                        ChatHost(Feed.TRIBES_DEFAULT_SERVER_URL),
                        durationRetrieverHandler
                    )
                }
            }
        }
    }

    private suspend fun restoreContentFeedStatusFrom(
        contentFeedStatus: ContentFeedStatusDto,
        queries: SphinxDatabaseQueries?,
        playingPodcastId: String?,
        playingEpisodeId: String?
    ) {
        val queries = queries ?: coreDB.getSphinxDatabaseQueries()
        var shouldRestoreItem = true

        val feed = getFeedById(FeedId(contentFeedStatus.feed_id)).firstOrNull()

        if (feed == null) {
            val chat = contentFeedStatus.chat_id?.toChatId()?.let { getChatById(it).firstOrNull() }

            contentFeedStatus.feed_url.toFeedUrl()?.let { feedUrl ->
                val response = updateFeedContent(
                    chatId = contentFeedStatus.chat_id?.toChatId() ?: ChatId(ChatId.NULL_CHAT_ID.toLong()),
                    host = ChatHost(Feed.TRIBES_DEFAULT_SERVER_URL),
                    feedUrl = feedUrl,
                    chatUUID = chat?.uuid,
                    subscribed = contentFeedStatus.subscription_status.toSubscribed(),
                    currentItemId = contentFeedStatus.item_id?.toFeedId(),
                    delay = 0
                )

                if (response is Response.Error) {
                    shouldRestoreItem = false
                }
            }
        }

        if (!shouldRestoreItem) {
            return
        }

        contentFeedLock.withLock {
            if (contentFeedStatus.feed_id == playingPodcastId) {
                queries.contentFeedStatusUpdate(
                    contentFeedStatus.subscription_status.toSubscribed(),
                    contentFeedStatus.chat_id?.toChatId(),
                    contentFeedStatus.sats_per_minute?.toSat(),
                    FeedId(contentFeedStatus.feed_id)
                )
            } else {
                queries.contentFeedStatusUpsert(
                    FeedUrl(contentFeedStatus.feed_url),
                    contentFeedStatus.subscription_status.toSubscribed(),
                    contentFeedStatus.chat_id?.toChatId(),
                    contentFeedStatus.item_id?.toFeedId(),
                    contentFeedStatus.sats_per_minute?.toSat(),
                    contentFeedStatus.player_speed?.toFeedPlayerSpeed(),
                    FeedId(contentFeedStatus.feed_id)
                )
            }
        }

        feedLock.withLock {
            queries.feedUpdateSubscribeAndChat(
                contentFeedStatus.subscription_status.toSubscribed(),
                contentFeedStatus.chat_id?.toChatId() ?: ChatId(ChatId.NULL_CHAT_ID.toLong()),
                FeedId(contentFeedStatus.feed_id)
            )
        }

        contentFeedStatus.episodes_status?.let { episodeStatuses ->
            for (episodeStatus in episodeStatuses) {
                for ((episodeId, status) in episodeStatus) {
                    if (playingEpisodeId == episodeId) {
                        continue
                    }
                    contentEpisodeLock.withLock {
                        queries.contentEpisodeStatusUpsert(
                            FeedItemDuration(status.duration),
                            FeedItemDuration(status.current_time),
                            FeedId(episodeId),
                            FeedId(contentFeedStatus.feed_id),
                            null
                        )
                    }
                }
            }
        }
    }

    override val appLogsStateFlow: MutableStateFlow<String> by lazy {
        MutableStateFlow("")
    }

    override fun setAppLog(log: String) {
        appLogsStateFlow.value = appLogsStateFlow.value + log + "\n"
    }

    override suspend fun clearDatabase() {
        val queries = coreDB.getSphinxDatabaseQueries()

        messageLock.withLock {
            chatLock.withLock {
                withContext(io) {
                    queries.transaction {
                        clearDatabase(
                            queries
                        )
                    }
                }
            }
        }
    }

    // Utility Methods
    @OptIn(ExperimentalUnsignedTypes::class)
    private fun generateRandomBytes(size: Int): ByteArray {
        val random = SecureRandom()
        val bytes = ByteArray(size)
        random.nextBytes(bytes)
        val byteArray = ByteArray(size)

        for (i in bytes.indices) {
            byteArray[i] = bytes[i]
        }

        return byteArray
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun ByteArray.toHex(): String =
    StringBuilder(size * 2).let { hex ->
        for (b in this) {
            hex.append(String.format("%02x", b, 0xFF))
        }
        hex.toString()
    }
