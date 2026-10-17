package tv.medusa.app.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import tv.medusa.app.data.local.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: UserPreferencesRepository
) : PurchasesUpdatedListener, BillingClientStateListener {

    companion object {
        const val PRODUCT_ID_PRO = "medusa_pro_lifetime"
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _proProductDetails = MutableStateFlow<ProductDetails?>(null)
    val proProductDetails: StateFlow<ProductDetails?> = _proProductDetails.asStateFlow()

    private val _billingStatusMessage = MutableStateFlow<String?>(null)
    val billingStatusMessage: StateFlow<String?> = _billingStatusMessage.asStateFlow()

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    init {
        startBillingConnection()
    }

    fun startBillingConnection() {
        if (!billingClient.isReady) {
            billingClient.startConnection(this)
        }
    }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            queryPurchases()
            queryProductDetails()
        } else {
            _billingStatusMessage.value = "Google Play no disponible: ${billingResult.debugMessage}"
        }
    }

    override fun onBillingServiceDisconnected() {
        _billingStatusMessage.value = "Conexión con Google Play perdida. Reintentando..."
    }

    fun queryPurchases() {
        if (!billingClient.isReady) return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchasesList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val hasPro = purchasesList.any { purchase ->
                    purchase.products.contains(PRODUCT_ID_PRO) &&
                            purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                scope.launch {
                    preferencesRepository.updatePurchasedPro(hasPro)
                }

                // Acknowledge any unacknowledged purchases
                purchasesList.forEach { purchase ->
                    if (!purchase.isAcknowledged && purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        acknowledgePurchase(purchase)
                    }
                }
            }
        }
    }

    fun queryProductDetails() {
        if (!billingClient.isReady) return
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID_PRO)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                _proProductDetails.value = productDetailsList.firstOrNull { it.productId == PRODUCT_ID_PRO }
            }
        }
    }

    fun launchBillingFlow(activity: Activity): Boolean {
        val details = _proProductDetails.value ?: return false
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .build()
        )
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        val responseCode = billingClient.launchBillingFlow(activity, billingFlowParams).responseCode
        return responseCode == BillingClient.BillingResponseCode.OK
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (!purchases.isNullOrEmpty()) {
                    purchases.forEach { purchase ->
                        if (purchase.products.contains(PRODUCT_ID_PRO) &&
                            purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                        ) {
                            scope.launch {
                                preferencesRepository.updatePurchasedPro(true)
                            }
                            if (!purchase.isAcknowledged) {
                                acknowledgePurchase(purchase)
                            }
                        }
                    }
                    _billingStatusMessage.value = "¡Medusa Pro desbloqueado con éxito!"
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _billingStatusMessage.value = "Compra cancelada"
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                scope.launch {
                    preferencesRepository.updatePurchasedPro(true)
                }
                _billingStatusMessage.value = "Ya posees Medusa Pro. Licencia restaurada."
            }
            else -> {
                _billingStatusMessage.value = "Error en la compra: ${billingResult.debugMessage}"
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { /* Purchase acknowledged */ }
    }
}
