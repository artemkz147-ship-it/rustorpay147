package ru.anime.bridge

import android.os.Handler
import android.os.Looper
import ru.rustore.sdk.pay.RuStorePayClient
import ru.rustore.sdk.pay.model.*

/** Grants only store-confirmed purchases; local durable IDs make retries idempotent. */
class PaymentController(
    private val grant: (String) -> Boolean,
    private val onPrice: (String?) -> Unit,
    private val onBusy: (Boolean) -> Unit,
    private val onMessage: (String) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var closed = false
    private var recovering = false
    private var purchasing = false
    private val processing = mutableSetOf<String>()
    private fun main(block: () -> Unit) { handler.post { if (!closed) block() } }
    private val client get() = RuStorePayClient.instance

    fun refresh() {
        if (closed) return
        runCatching {
            client.getProductInteractor().getProducts(listOf(ProductId(PRODUCT)))
                .addOnSuccessListener { products -> main {
                    val product = products.firstOrNull { it.productId.value == PRODUCT && it.type == ProductType.CONSUMABLE_PRODUCT }
                    onPrice(product?.amountLabel?.value)
                } }
                .addOnFailureListener { main { onPrice(null) } }
        }.onFailure { onPrice(null) }
        recover()
    }

    fun purchase() {
        if (closed || purchasing) return
        purchasing = true
        onBusy(true)
        runCatching {
            client.getPurchaseInteractor().purchase(
                params = ProductPurchaseParams(ProductId(PRODUCT)),
                preferredPurchaseType = PreferredPurchaseType.ONE_STEP,
                sdkTheme = SdkTheme.DARK
            ).addOnSuccessListener { result -> main {
                purchasing = false
                // Read the authoritative status rather than treating a held payment as paid.
                resolve(result.purchaseId)
                recover()
                onBusy(false)
            } }.addOnFailureListener { error -> main {
                purchasing = false
                onBusy(false)
                if (error !is RuStorePaymentException.ProductPurchaseCancelled)
                    onMessage("Покупка не завершена. Проверь RuStore и попробуй снова.")
                recover()
            } }
        }.onFailure {
            purchasing = false; onBusy(false)
            onMessage("Оплата RuStore сейчас недоступна. Попробуй позже.")
        }
    }

    private fun resolve(id: PurchaseId) {
        runCatching {
            client.getPurchaseInteractor().getPurchase(id)
                .addOnSuccessListener { purchase -> main { if (purchase is ProductPurchase) process(purchase) } }
                .addOnFailureListener { main { onMessage("Оплата обрабатывается. Буст восстановится при следующем запуске.") } }
        }.onFailure { onMessage("Оплата обрабатывается. Буст восстановится при следующем запуске.") }
    }

    fun recover() {
        if (closed || recovering || purchasing) return
        recovering = true
        runCatching {
            client.getPurchaseInteractor().getPurchases(productType = ProductType.CONSUMABLE_PRODUCT)
                .addOnSuccessListener { purchases -> main {
                    recovering = false
                    purchases.filterIsInstance<ProductPurchase>().forEach { process(it) }
                } }.addOnFailureListener { main { recovering = false } }
        }.onFailure { recovering = false }
    }

    private fun process(purchase: ProductPurchase) {
        if (purchase.productId.value != PRODUCT || purchase.quantity.value != 1) return
        val id = purchase.purchaseId.value
        if (!processing.add(id)) return
        when (purchase.status) {
            ProductPurchaseStatus.PAID -> runCatching {
                client.getPurchaseInteractor().confirmTwoStepPurchase(purchase.purchaseId)
                    .addOnSuccessListener { main { processing.remove(id); resolve(purchase.purchaseId) } }
                    .addOnFailureListener { main { processing.remove(id) } }
            }.onFailure { processing.remove(id) }
            ProductPurchaseStatus.CONFIRMED -> {
                if (!grant(id)) {
                    processing.remove(id)
                    onMessage("Не удалось сохранить буст. Покупка будет восстановлена при следующем запуске.")
                    return
                }
                if (purchase.acknowledgementState == AcknowledgementState.ACKNOWLEDGED) {
                    processing.remove(id)
                    return
                }
                runCatching {
                    client.getPurchaseInteractor().updateAcknowledgementState(purchase.purchaseId, AcknowledgementState.ACKNOWLEDGED)
                        .addOnSuccessListener { main { processing.remove(id) } }
                        .addOnFailureListener { main { processing.remove(id) } }
                }.onFailure { processing.remove(id) }
            }
            else -> processing.remove(id)
        }
    }

    fun close() { closed = true; handler.removeCallbacksAndMessages(null) }
    companion object { const val PRODUCT = "bust1471" }
}
