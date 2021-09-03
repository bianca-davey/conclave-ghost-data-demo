package com.r3.conclave.apps.orderbook.gui

import com.r3.conclave.apps.orderbook.types.BuySell
import com.r3.conclave.apps.orderbook.types.Order
import javafx.scene.control.TextField
import javafx.scene.control.TextFormatter
import javafx.scene.control.ToggleButton
import javafx.util.converter.CurrencyStringConverter
import java.text.NumberFormat
import java.util.*

class NewOrderController(private val userName: String) : Controller<Order>("/new-order.fxml", "New Order") { // Controller<Order.(/new-order.fxml", "New Order"
    lateinit var buyButton: ToggleButton
    lateinit var sellButton: ToggleButton

    init {
        // We're abusing buttons as if they were radio buttons, but JavaFX allows a button to
        // be unselected if just using a regular toggle button. So we have to enforce radio-like
        // behaviour here.
        buyButton.setOnAction { if (!buyButton.isSelected) buyButton.isSelected = true }
        sellButton.setOnAction { if (!sellButton.isSelected) sellButton.isSelected = true }
    }

    lateinit var instrumentField: TextField
    lateinit var priceField: TextField
    private val locale = Locale.US
    private val priceFieldFormatter = TextFormatter(CurrencyStringConverter(locale), 0.0)
    lateinit var quantityField: TextField

    init {
        priceField.textFormatter = priceFieldFormatter
        priceField.setOnKeyTyped {
            if (!priceField.text.startsWith("$")) {
                val pos = priceField.caretPosition
                priceField.text = "$" + priceField.text
                priceField.positionCaret(pos + 1)
            }
        }
        redUnderlineIfBlank(instrumentField)
        redUnderlineIfBlank(quantityField)
    }

    private fun redUnderlineIfBlank(textField: TextField) {
        textField.styleClass += "text-field-invalid"
        textField.setOnKeyTyped {
            if (textField.text.isBlank()) {
                textField.styleClass += "text-field-invalid"
            } else {
                textField.styleClass -= "text-field-invalid"
            }
        }
    }

    private val price get() = (priceFieldFormatter.value.toDouble() * 100).toLong()

    fun ok() {
        val buySell = when {
            buyButton.isSelected -> BuySell.BUY
            sellButton.isSelected -> BuySell.SELL
            else -> unreachable()
        }
        val quantity = NumberFormat.getIntegerInstance().parse(quantityField.text).toLong()
        writeableResult.set(Order(nextOrderId++, userName, buySell, instrumentField.text, price, quantity))
        close()
    }
}