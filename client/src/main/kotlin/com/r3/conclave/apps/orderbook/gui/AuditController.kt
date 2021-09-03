package com.r3.conclave.apps.orderbook.gui

import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.common.EnclaveSecurityInfo
import com.sandec.mdfx.MDFXNode
import javafx.scene.control.ScrollPane
import javafx.geometry.Insets

open class AuditController(ra: EnclaveInstanceInfo) : Controller<Unit>("/audit.fxml", "Audit Results") {
    lateinit var contentScroll: ScrollPane

    init {
        val markdown = if (ra.securityInfo.summary == EnclaveSecurityInfo.Summary.INSECURE) {
            """
                ## Service is not tamperproof
                
                This service is **not** currently protected from the service operator. It is running in a developer
                diagnostics mode and should not be used with sensitive data.
                
                ${ra.securityInfo.reason}
            """.trimIndent()
        } else {
            val extra = if (ra.securityInfo.summary == EnclaveSecurityInfo.Summary.STALE)
                """
                    **WARNING:** The server is behind on maintenance. There may be attacks the service operator could
                    perform to extract sensitive data. You should contact the service operator and ask them to perform 
                    the necessary maintenance.
                """.trimIndent()
            else
                ""

            """## AuditCorp Ltd

This application receives orders and emits trades. The service operator **can see**:

- When you submit an order.
- Who is submitting orders.
- When a trade matches.

The service operator **cannot see**:

- The contents of an order.
- The contents of a trade.

This policy was checked automatically and is being enforced. $extra

## Technical data

$ra
            """.trimIndent()
        }
        contentScroll.content = MDFXNode(markdown).also {
            it.padding = Insets(20.0)
        }
    }
}