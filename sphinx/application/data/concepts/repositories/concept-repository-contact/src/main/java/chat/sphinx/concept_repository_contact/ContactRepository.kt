package chat.sphinx.concept_repository_contact

import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.Response
import chat.sphinx.kotlin_response.ResponseError
import chat.sphinx.wrapper_common.PhotoUrl
import chat.sphinx.wrapper_common.dashboard.ContactId
import chat.sphinx.wrapper_common.dashboard.InviteId
import chat.sphinx.wrapper_common.lightning.LightningNodePubKey
import chat.sphinx.wrapper_common.lightning.LightningRouteHint
import chat.sphinx.wrapper_common.lightning.Sat
import chat.sphinx.wrapper_contact.*
import chat.sphinx.wrapper_invite.Invite
import chat.sphinx.wrapper_invite.InviteString
import chat.sphinx.wrapper_io_utils.InputStreamProvider
import chat.sphinx.wrapper_message_media.MediaType
import io.matthewnelson.crypto_common.clazzes.Password
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * All [Contact]s are cached to the DB such that a network refresh will update
 * them, and thus proc and [Flow] being collected.
 * */
interface ContactRepository {
    /** Sphinx V2 (rename methods for clarity) **/

    val accountOwner: StateFlow<Contact?>
    suspend fun getOwnerContact(): Contact?
    val getAllContacts: Flow<List<Contact>>
    fun getContactById(contactId: ContactId): Flow<Contact?>
    fun getContactByPubKey(pubKey: LightningNodePubKey): Flow<Contact?>
    suspend fun getAllContactsByIds(contactIds: List<ContactId>): List<Contact>

    // Need to review DB upsert when setting invites on upsertNewContact query
    fun getInviteByContactId(contactId: ContactId): Flow<Invite?>
    fun getInviteById(inviteId: InviteId): Flow<Invite?>
    fun getInviteByString(inviteString: InviteString): Flow<Invite?>

    var updatedContactIds: MutableList<ContactId>

    suspend fun deleteContactById(contactId: ContactId): Response<Any, ResponseError>
    suspend fun updateOwnerDeviceId(deviceId: DeviceId)
    suspend fun updateOwnerNameAndKey(name: String, contactKey: Password): Response<Any, ResponseError>
    suspend fun updateOwner(alias: String?, privatePhoto: PrivatePhoto?, tipAmount: Sat?): Response<Any, ResponseError>


    // TODO: add chatId to argument to update alias photo
    suspend fun updateProfilePic(
//        chatId: ChatId?,
        stream: InputStreamProvider,
        mediaType: MediaType,
        fileName: String,
        contentLength: Long?
    ): Response<Any, ResponseError>

    suspend fun toggleContactBlocked(contact: Contact): Response<Boolean, ResponseError>

    suspend fun setGithubPat(pat: String): Response<Boolean, ResponseError>

    suspend fun createOwner(okKey: String, routeHint: String, shortChannelId: String)

    suspend fun createNewContact(contact: NewContact)

    suspend fun updateOwnerAlias(alias: ContactAlias)

    suspend fun getNewContactIndex(): Flow<ContactId?>

    fun saveNewContactRegistered(msgSender: String, date: Long?)

    fun updateNewContactInvited(contact: NewContact)


    /** Sphinx V1 (likely to be removed) **/

    suspend fun updateContact(
        contactId: ContactId,
        alias: ContactAlias?,
        routeHint: LightningRouteHint?
    ): Response<Any, ResponseError>

}
