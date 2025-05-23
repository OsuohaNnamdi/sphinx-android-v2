package chat.sphinx.onboard_ready.ui

import android.content.Context
import androidx.lifecycle.viewModelScope
import chat.sphinx.concept_network_query_invite.NetworkQueryInvite
import chat.sphinx.concept_repository_connect_manager.ConnectManagerRepository
import chat.sphinx.concept_repository_contact.ContactRepository
import chat.sphinx.concept_repository_lightning.LightningRepository
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.Response
import chat.sphinx.kotlin_response.ResponseError
import chat.sphinx.onboard_common.OnBoardStepHandler
import chat.sphinx.onboard_ready.navigation.OnBoardReadyNavigator
import chat.sphinx.wrapper_common.lightning.LightningNodePubKey
import chat.sphinx.wrapper_common.lightning.toLightningRouteHint
import chat.sphinx.wrapper_contact.ContactAlias
import chat.sphinx.wrapper_lightning.NodeBalance
import chat.sphinx.wrapper_lightning.NodeBalanceAll
import dagger.hilt.android.lifecycle.HiltViewModel
import io.matthewnelson.android_feature_viewmodel.SideEffectViewModel
import io.matthewnelson.android_feature_viewmodel.submitSideEffect
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.annotation.meta.Exhaustive
import javax.inject.Inject

@HiltViewModel
internal class OnBoardReadyViewModel @Inject constructor(
    dispatchers: CoroutineDispatchers,
    private val navigator: OnBoardReadyNavigator,
    private val lightningRepository: LightningRepository,
    private val onBoardStepHandler: OnBoardStepHandler,
): SideEffectViewModel<
        Context,
        OnBoardReadySideEffect,
        OnBoardReadyViewState
        >(dispatchers, OnBoardReadyViewState.Idle)
{
    fun finishSignup() {
        viewModelScope.launch(mainImmediate) {
            onBoardStepHandler.finishOnboard()
            goToDashboard()
        }
    }

    private suspend fun goToDashboard() {
        viewModelScope.launch(mainImmediate) {
            onBoardStepHandler.finishOnBoardSteps()
            navigator.toDashboardScreen()
        }
    }

    suspend fun getBalances(): StateFlow<NodeBalance?> =
        lightningRepository.getAccountBalance()
}
