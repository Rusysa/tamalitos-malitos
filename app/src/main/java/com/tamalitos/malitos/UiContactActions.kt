package com.tamalitos.malitos

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri

internal fun MainActivity.openContactAction(intent: Intent) {
    try { startActivity(intent) }
    catch (_: ActivityNotFoundException) { message("No hay una aplicación disponible para esta acción.") }
    catch (_: SecurityException) { message("Android no permitió abrir esta aplicación.") }
}

internal fun MainActivity.dialCustomer(phone: String) {
    if (phone.isNotBlank()) openContactAction(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone.trim(), null)))
}

internal fun MainActivity.openAddress(address: String) {
    if (address.isNotBlank()) openContactAction(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(address.trim())}")))
}

internal fun MainActivity.shareOrder(order: Order) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Tamalitos Malitos · Pedido #${order.id}")
        putExtra(Intent.EXTRA_TEXT, orderSummary(order, statusLabel(order.status)))
    }
    openContactAction(Intent.createChooser(intent, "Compartir resumen del pedido"))
}
