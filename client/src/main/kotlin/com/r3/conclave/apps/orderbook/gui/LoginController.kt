package com.r3.conclave.apps.orderbook.gui

import com.r3.conclave.grpc.ConclaveGRPCClient
import javafx.scene.Parent
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.TextField

class LoginController : Controller<Unit>("/intro.fxml", "Login") {
    lateinit var serviceAddress: TextField
    lateinit var userName: TextField
    lateinit var password: TextField
    lateinit var connectButton: Button

    // The UI shown during connect.
    lateinit var progressLabel: Label
    lateinit var verifyingImage: Parent
    lateinit var communicatingImage: Parent
    lateinit var signinImage: Parent

    init {
        // The UI has some dummy state to make visual editing easier, clear it now.
        progressLabel.text = ""
        verifyingImage.opacity = 0.0
        communicatingImage.opacity = 0.0
        signinImage.opacity = 0.0

        userName.text = "alice"
        password.text = "alice"
        serviceAddress.text = "localhost:9999"
    }

    fun onConnect() {
        connectButton.isDisable = true
        progressLabel.text = "Verifying server behaviour ..."
        verifyingImage.opacity = 1.0
        val task = ConnectTask(serviceAddress.text, userName.text, password.text)
        task.progressEnumProperty.addListener { _, _, new ->
            when (new) {
                ConclaveGRPCClient.Progress.COMPLETING -> {
                    // Setting up the mail stream.
                    communicatingImage.opacity = 1.0
                    progressLabel.text = "Encrypting communication ..."
                }
                ConclaveGRPCClient.Progress.COMPLETE -> {
                    // Connection may be complete, but not the signing in part ...
                    progressLabel.text = "Signing in ..."
                    signinImage.opacity = 1.0
                }
            }
        }
        Thread(task, "Connect Thread").also { it.isDaemon = true }.start()
        task.setOnSucceeded {
            OrderBookGUI.primaryStage.scene = MainUIController(userName.text, client = task.value).scene
        }
        task.setOnFailed {
            throw task.exception
        }
    }
}