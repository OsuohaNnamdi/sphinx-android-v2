package chat.sphinx.contact.ui

import android.app.Application
import android.content.Context
import android.widget.ImageView
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavArgs
import chat.sphinx.concept_image_loader.ImageLoader
import chat.sphinx.concept_repository_connect_manager.ConnectManagerRepository
import chat.sphinx.concept_repository_contact.ContactRepository
import chat.sphinx.concept_repository_lightning.LightningRepository
import chat.sphinx.concept_repository_subscription.SubscriptionRepository
import chat.sphinx.concept_view_model_coordinator.ViewModelCoordinator
import chat.sphinx.concept_wallet.WalletDataHandler
import chat.sphinx.contact.R
import chat.sphinx.contact.navigation.ContactNavigator
import chat.sphinx.kotlin_response.Response
import chat.sphinx.scanner_view_model_coordinator.request.ScannerFilter
import chat.sphinx.scanner_view_model_coordinator.request.ScannerRequest
import chat.sphinx.scanner_view_model_coordinator.response.ScannerResponse
import chat.sphinx.wrapper_common.dashboard.ContactId
import chat.sphinx.wrapper_common.lightning.*
import chat.sphinx.wrapper_contact.ContactAlias
import chat.sphinx.resources.R as R_common
import com.squareup.moshi.Moshi
import io.matthewnelson.android_feature_viewmodel.SideEffectViewModel
import io.matthewnelson.android_feature_viewmodel.submitSideEffect
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

abstract class ContactViewModel<ARGS: NavArgs>(
    val navigator: ContactNavigator,
    dispatchers: CoroutineDispatchers,
    private val app: Application,
    protected val contactRepository: ContactRepository,
    protected val subscriptionRepository: SubscriptionRepository,
    protected val scannerCoordinator: ViewModelCoordinator<ScannerRequest, ScannerResponse>,
    val walletDataHandler: WalletDataHandler,
    val connectManagerRepository: ConnectManagerRepository,
    val moshi: Moshi,
    val lightningRepository: LightningRepository,
    val imageLoader: ImageLoader<ImageView>
): SideEffectViewModel<
        Context,
        ContactSideEffect,
        ContactViewState
        >(dispatchers, ContactViewState.Idle)
{
    protected abstract val args: ARGS

    protected abstract val fromAddFriend: Boolean
    protected abstract val contactId: ContactId?
    protected var createContactJob: Job? = null

    abstract fun initContactDetails()

    private var scannerJob: Job? = null
    fun requestScanner() {
        if (scannerJob?.isActive == true) {
            return
        }

        scannerJob = viewModelScope.launch(mainImmediate) {
            val response = scannerCoordinator.submitRequest(
                ScannerRequest(
                    filter = object : ScannerFilter() {
                        override suspend fun checkData(data: String): Response<Any, String> {
                            val scannedString = data.split("_")
                            val contactRouteHint = "${scannedString.getOrNull(1)}_${scannedString.getOrNull(2)}".toLightningRouteHint()

                            if (scannedString.getOrNull(0)?.toLightningNodePubKey() != null) {
                                return Response.Success(Any())
                            }
                            if (contactRouteHint != null) {
                                return Response.Success(Any())
                            }
                            return Response.Error("QR code is not a Lightning Node Public Key")
                        }
                    }
                )
            )
            if (response is Response.Success) {
                val contactInfo = response.value.value.split("_")
                val contactOkKey = contactInfo.getOrNull(0)?.toLightningNodePubKey()
                val contactRouteHint = "${contactInfo.getOrNull(1)}_${contactInfo.getOrNull(2)}".toLightningRouteHint()

                if (contactOkKey != null && contactRouteHint != null) {
                    submitSideEffect(
                        ContactSideEffect.ContactInfo(
                            contactOkKey,
                            contactRouteHint
                        )
                    )
                }
            }
        }
    }

    protected var saveContactJob: Job? = null
    fun saveContact(contactAlias: String?, lightningNodePubKey: String?, lightningRouteHint: String?) {
        if (saveContactJob?.isActive == true) {
            return
        }

        saveContactJob = viewModelScope.launch {

            if (contactAlias.isNullOrEmpty()) {
                submitSideEffect(ContactSideEffect.Notify.NicknameAndAddressRequired)
                return@launch
            }

            if (lightningNodePubKey.isNullOrEmpty() || lightningNodePubKey.toLightningNodePubKey() == null) {
                submitSideEffect(ContactSideEffect.Notify.InvalidLightningNodePublicKey)
                return@launch
            }

            if (lightningRouteHint.isNullOrEmpty() || lightningRouteHint.toLightningRouteHint() == null) {
                submitSideEffect(ContactSideEffect.Notify.InvalidRouteHint)
                return@launch
            }

            createContact(
                ContactAlias(contactAlias),
                LightningNodePubKey(lightningNodePubKey),
                lightningRouteHint?.toLightningRouteHint(),
            )
        }
    }

    protected abstract fun createContact(
        contactAlias: ContactAlias,
        lightningNodePubKey: LightningNodePubKey,
        lightningRouteHint: LightningRouteHint?,
    )

    fun toQrCodeLightningNodePubKey(nodePubKey: String) {
        viewModelScope.launch(mainImmediate) {
            navigator.toQRCodeDetail(
                nodePubKey,
                app.getString(R_common.string.public_key),
                ""
            )
        }

    }
}
