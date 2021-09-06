package com.r3.conclave.apps.orderbook.gui

import com.r3.conclave.apps.orderbook.OrderBookClient
import com.r3.conclave.apps.orderbook.types.*
import com.r3.conclave.grpc.ConclaveGRPCClient
import javafx.application.Application
import javafx.application.Platform
import javafx.beans.binding.Bindings
import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.concurrent.Task
import javafx.event.EventHandler
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.DragEvent
import javafx.scene.input.TransferMode
import javafx.scene.layout.HBox
import javafx.scene.shape.Circle
import javafx.scene.text.Font
import javafx.stage.Stage
import kotlinx.serialization.ExperimentalSerializationApi
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.system.exitProcess


internal var nextOrderId = 0
// TODO: delete if not using.
private var nextPersonId = 0

fun unreachable(): Nothing = error("Unreachable")

/**
 * Controls a UI loaded from the given FXML file. The UI may generate a result object of some sort when it's closed.
 * A helper method is provided to show the UI in a modal window.
 */
open class Controller<RESULT>(fxmlPath: String, private val title: String) {
    val scene: Scene = run {
        val fxml = FXMLLoader(javaClass.getResource(fxmlPath))
        fxml.setController(this)
        fxml.load()
    }

    protected val writeableResult = ReadOnlyObjectWrapper<RESULT?>()
    fun resultProperty(): ReadOnlyObjectProperty<RESULT?> = writeableResult.readOnlyProperty
    val result: RESULT? get() = resultProperty().get()

    private var stage: Stage? = null

    /**
     * Shows the UI and blocks until the user has closed the window, returning the result object or null if the user
     * cancelled. The UI must have a button or other control that calls [close].
     */
    fun showAndWait(): RESULT? {
        val stage = Stage().also {
            it.title = title
            it.scene = scene
        }
        this.stage = stage
        stage.showAndWait()
        return result
    }

    fun close() {
        stage?.close()
    }
}

@Suppress("UNCHECKED_CAST")
class MainUIController(userName: String, private val client: OrderBookClient) : Controller<Unit>("/main.fxml", "Order Book Demo") {

    class Item<T : OrderOrTrade>(val date: Instant, val contained: T)

    lateinit var ordersTable: TableView<Item<Order>>
    lateinit var matchesTable: TableView<Item<Trade>>
    lateinit var userButton: MenuButton
    lateinit var photoView: ImageView

    private val orders = FXCollections.observableArrayList<Item<Order>>()
    private val trades = FXCollections.observableArrayList<Item<Trade>>()

    lateinit var personEntityButton: Button
    lateinit var invoiceEntityButton: Button
    lateinit var claimEntityButton: Button

    // TODO: for entity table with input fields in table row. Incomplete- paused.
    //class PersonInstance<T: EntityType>(val date: Instant, val contained: T)
    class PersonInstance<T: EntityType>()
    private val personEntities = FXCollections.observableArrayList<Person>()
    private val personEntityExamples = FXCollections.observableArrayList<Person>(
        Person(nextPersonId, "Jane", "Doe", "06.03.1996", "123 Fake Street", "456781234"),
        Person(nextPersonId, "John", "Doe", "12.12.1989", "321 Fake Street", "123456789"),
    )
    lateinit var entityTable: TableView<Person>

    // TODO: input fields.
    lateinit var firstNameField: TextField
    lateinit var lastNameField: TextField
    lateinit var dOBField: TextField
    lateinit var addressField: TextField
    lateinit var passportField: TextField

    // TODO: person input fields.
    fun add() {
        writeableResult.set(Person(nextPersonId++, firstNameField.text, lastNameField.text, dOBField.text, addressField.text, passportField.text))
    }

    init {
        userButton.text = userName.capitalize()
        matchesTable.items = trades
        ordersTable.items = orders
        // TODO: entity table for person.
        //personTable.items = personEntities

        // Setting user profile details.
        if (userName.toLowerCase() == "alice")
            photoView.image = Image("/face1.png")
        else
            photoView.image = Image("/face2.jpg")
        val radius = photoView.boundsInLocal.width / 2.0
        photoView.clip = Circle(radius).also { it.centerX = radius; it.centerY = radius }

        configureOrdersTable()
        configureMatchesTables()
        // TODO: entity table for person.
        //configurePersonTable()
        configureEntityTable()
        listenForMatches()
    }

    // TODO drag & drop for CSV file.
    lateinit var dropBox: HBox;
    /*
    dropBox.setOnDragOver(EventHandler<DragEvent>){
        @Override
        fun handle(DragEvent event){
            Dragboard dragBoard = event.getDragBoard();
            if(dragBoard.hasFiles()){
                event.acceptTransferModes(TransferMode.COPY)
            }
        }
    }
    */

    // TODO: CSV drop box.
    private fun csvDropBox() {
        dropBox.onDragOver = EventHandler<DragEvent>(){
            fun handle(event: DragEvent){
                if(event.gestureSource != dropBox && event.dragboard.hasFiles()){
                    event.acceptTransferModes(TransferMode.COPY)
                }
                event.consume()
            }
        }
        dropBox.onDragDropped = EventHandler<DragEvent>(){
            fun handle(event: DragEvent){
                var fileText: String
                val db = event.dragboard
                if (db.hasFiles()){
                    fileText = db.files.toString()
                    //fileText.setText(db.getFiles().toString())
                    println("Received file.")
                    println("Contents: ")
                    print(fileText)
                }
                event.consume()
            }
        }
    }

    /*
        private fun csvDropBox() {
        dropBox.setOnDragOver(EventHandler<DragEvent>(){
            @Override
            handle(DragEvent event){
            }
        }

        dropBox.setOnDragDropped(){
        }
    }
     */

    private fun <T : OrderOrTrade> setColFromString(column: TableColumn<Item<T>, *>?, body: (Item<T>) -> String) {
        (column as TableColumn<Item<T>, String>).setCellValueFactory {
            Bindings.createStringBinding({ body(it.value) })
        }
    }

    // TODO: person-entity table, note- incomplete.
    // <PersonInstance>-<Item>, <EntityType>-<OrderOrTrade>
    private fun <T: EntityType> setColumn(column: TableColumn<PersonInstance<T>, *>?, body: (PersonInstance<T>) -> String) {
        (column as TableColumn<PersonInstance<T>, String>).setCellValueFactory {
            Bindings.createStringBinding({body(it.value)})
        }
    }

    private val formatter = NumberFormat.getCurrencyInstance(Locale.US)
    private fun Long.toPriceString(): String = formatter.format(toDouble() / 100.0)

    // TODO: person entity table.
    private fun configureEntityTable(){
        entityTable.items(personEntityExamples)
        entityTable.columns.addAll()
    }

    // TODO: configure entity-person table. note- incomplete.
    // Doesn't recognise configureColumns.
    private fun configurePersonTable(){
        //personTable.columns.centreColumns()
        //setColumn(personTable.columns[0]) {it.contained.personId.toString}

        /*
        None of the following candidates is applicable because of receiver type mismatch:
    private final fun <T : EntityType> List<TableColumn<MainUIController.PersonInstance<TypeVariable(T)>, *>>.centreColumns():
    Unit defined in com.r3.conclave.apps.orderbook.gui.MainUIController
    private final fun <T : EntityType> List<TableColumn<MainUIController.PersonInstance<T>, *>>.centreColumns(): Unit
         */
    }

    private fun configureOrdersTable() {
        ordersTable.columns.centerTableColumns()
        setColFromString(ordersTable.columns[0]) { it.contained.id.toString() }
        configureTimestampColumn(ordersTable, 1)
        setColFromString(ordersTable.columns[2]) { it.contained.side.toString() }
        setColFromString(ordersTable.columns[3]) { it.contained.instrument }
        setColFromString(ordersTable.columns[4]) { it.contained.price.toPriceString() }
        setColFromString(ordersTable.columns[5]) { it.contained.quantity.toString() }
    }

    private fun configureMatchesTables() {
        matchesTable.columns.centerTableColumns()
        setColFromString(matchesTable.columns[0].columns[0]) { it.contained.orderId.toString() }
        setColFromString(matchesTable.columns[0].columns[1]) { it.contained.matchingOrder.id.toString() }
        configureTimestampColumn(matchesTable, 1)
        setColFromString(matchesTable.columns[2]) { it.contained.matchingOrder.party.capitalize() }
        setColFromString(matchesTable.columns[3]) { it.contained.matchingOrder.side.toString() }
        setColFromString(matchesTable.columns[4]) { it.contained.matchingOrder.instrument }
        setColFromString(matchesTable.columns[5]) { it.contained.matchingOrder.price.toPriceString() }
        setColFromString(matchesTable.columns[6]) { it.contained.matchingOrder.quantity.toString() }
    }

    // TODO: for entity-person tableview, incomplete.
    private fun <T : EntityType> List<TableColumn<PersonInstance<T>, *>>.centreColumns() {
        for (column in this) {
            if (column.columns.isNotEmpty()) {
                // Handle sub-columns.
                column.columns.centreColumns()
            } else {
                (column as TableColumn<PersonInstance<T>, Any?>).setCellFactory {
                    object : TableCell<PersonInstance<T>, Any?>() {
                        override fun updateItem(item: Any?, empty: Boolean) {
                            super.updateItem(item, empty)
                            text = if (empty) "" else item?.toString() ?: ""
                            style = "-fx-alignment: center"
                        }
                    }
                }
            }
        }
    }

    private fun <T : OrderOrTrade> List<TableColumn<Item<T>, *>>.centerTableColumns() {
        for (column in this) {
            if (column.columns.isNotEmpty()) {
                // Handle sub-columns.
                column.columns.centerTableColumns()
            } else {
                (column as TableColumn<Item<T>, Any?>).setCellFactory {
                    object : TableCell<Item<T>, Any?>() {
                        override fun updateItem(item: Any?, empty: Boolean) {
                            super.updateItem(item, empty)
                            val quantity = when (val c = tableRow.item?.contained) {
                                null -> 0L
                                is Order -> c.quantity
                                is Trade -> c.matchingOrder.quantity
                                else -> unreachable()
                            }
                            text = if (empty) "" else item?.toString() ?: ""
                            style = "-fx-alignment: center"
                            if (quantity == 0L) style += "; -fx-text-fill: -color-4"
                        }
                    }
                }
            }
        }
    }

    private fun <T : OrderOrTrade> configureTimestampColumn(tableView: TableView<Item<T>>, i: Int) {
        setColFromString(tableView.columns[i]) { it ->
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG).format(it.date.atZone(ZoneId.systemDefault()))
        }
    }

    fun newTrade() {
        // Open the new trade screen in a new window and wait for it to be dismissed.
        val order: Order? = NewOrderController(client.userName).showAndWait()
        if (order != null) {
            client.sendOrder(order)
            orders.add(Item(Instant.now(), order))
        }
    }

    fun entitySelected(){

    }

    // Functions for implementing action upon entity type selection.
    fun personEntitySelected(){
        println("Person entity selected.")
        // Implement function to display and configure person entity interface.
    }
    fun invoiceEntitySelected(){
        println("Invoice entity selected.")
        // Implement function to display and configure person entity interface.
    }
    fun claimEntitySelected(){
        println("Claim entity selected.")
        // Implement function to display and configure person entity interface.
    }

    fun showAuditResults() {
        AuditController(client.enclaveInstanceInfo).showAndWait()
    }

    private fun listenForMatches() {
        thread(isDaemon = true, name = "listenForMatches") {
            while (!Thread.interrupted()) {
                try {
                    val orderOrTrade = client.waitForActivity(Long.MAX_VALUE, TimeUnit.SECONDS)!!
                    println(orderOrTrade)
                    Platform.runLater {
                        when (orderOrTrade) {
                            is Trade -> {
                                // Add the trade to the record.
                                val trade = orderOrTrade
                                trades.add(Item(Instant.now(), trade))
                                // Find the order it refers to and adjust it.
                                for (i in orders.indices) {
                                    val order = orders[i].contained
                                    if (order.id == trade.orderId) {
                                        // Update the timestamp, quantity.
                                        val newQuantity = order.quantity - minOf(order.quantity, trade.matchingOrder.quantity)
                                        check(newQuantity >= 0)

                                        orders[i] = Item(Instant.now(), order.copy(
                                                quantity = newQuantity
                                        ))
                                    }
                                }
                            }
                            is Order -> orders.add(Item(Instant.now(), orderOrTrade))
                        }
                    }
                } catch (e: InterruptedException) {
                    break
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        }
    }
}

// TODO: person table view.
private fun <S> TableView<S>.items(personEntityExamples: ObservableList<S>) {
}

// TODO person input field, from writeableResult.
private fun <T> ReadOnlyObjectWrapper<T>.set(person: Person) {
}

class OrderBookGUI : Application() {
    override fun start(primaryStage: Stage) {
        for (w in listOf("Regular", "Bold", "BoldItalic", "Italic", "SemiBold", "SemiBoldItalic"))
            checkNotNull(Font.loadFont(javaClass.getResourceAsStream("/fonts/Nunito/Nunito-$w.ttf"), 0.0))

        OrderBookGUI.primaryStage = primaryStage
        primaryStage.title = "Conclave Order Book"
        primaryStage.scene = LoginController().scene
        primaryStage.isMaximized = true
        primaryStage.show()

        // These two lines tell JFX that we don't want to resize the window to fit the scene as it changes.
        // Without it, the window would change size as we navigated between the login and main screens.
        primaryStage.width = primaryStage.width
        primaryStage.height = primaryStage.height

        // Pop up a message on crash.
        Thread.setDefaultUncaughtExceptionHandler { _, original ->
            println("Error in thread " + Thread.currentThread().name)
            original.printStackTrace()
            var e = original
            while (e.cause != null) e = e.cause
            Platform.runLater {
                Alert(Alert.AlertType.ERROR, e.message, ButtonType.CLOSE).showAndWait()
                exitProcess(1)
            }
        }
    }

    companion object {
        lateinit var primaryStage: Stage

        @JvmStatic
        fun main(args: Array<String>) = launch(OrderBookGUI::class.java, *args)
    }
}

class SignInException(response: Message) : Exception("Sign in failed: ${response::class.simpleName}")


class ConnectTask(
        private val serviceAddress: String,
        private val userName: String,
        private val password: String
) : Task<OrderBookClient>() {
    private val _progressEnumProperty = ReadOnlyObjectWrapper<ConclaveGRPCClient.Progress?>(this, "progressEnum")
    val progressEnumProperty: ReadOnlyObjectProperty<ConclaveGRPCClient.Progress?> = _progressEnumProperty.readOnlyProperty

    @OptIn(ExperimentalSerializationApi::class)
    override fun call(): OrderBookClient {
        val client = OrderBookClient(serviceAddress, userName, password)
        client.start { Platform.runLater { _progressEnumProperty.set(it) } }
        return client
    }
}