package com.parking;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class Main extends Application {

    private static final int TOTAL_SLOTS = 10;
    private static final int FEE_PER_HOUR = 20;

    private final String[] vehicles = new String[TOTAL_SLOTS];
    private final String[] owners = new String[TOTAL_SLOTS];
    private final String[] vehicleTypes = new String[TOTAL_SLOTS];
    private final LocalDateTime[] entryTimes = new LocalDateTime[TOTAL_SLOTS];
    private final int[] bookedHours = new int[TOTAL_SLOTS];
    private final Label[] slotLabels = new Label[TOTAL_SLOTS];

    private Label totalValue;
    private Label availableValue;
    private Label occupiedValue;
    private Label revenueValue;

    private final Path ACTIVE_FILE = Path.of("active_parking.txt");
    private final Path HISTORY_FILE = Path.of("parking_history.txt");
    private final Path PAYMENT_FILE = Path.of("payment_records.csv");
    private final Path RECEIPT_FOLDER = Path.of("receipts");

    private final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    @Override
    public void start(Stage stage) {
        // Modern admin dashboard layout. Existing parking actions and database logic are preserved.
        Label brand = new Label("PARK\nSMART");
        brand.setFont(Font.font("Arial", 25));
        brand.setTextFill(Color.WHITE);
        brand.setStyle("-fx-font-weight: bold; -fx-line-spacing: 4px;");

        Label brandSub = new Label("PARKING MANAGEMENT");
        brandSub.setFont(Font.font("Arial", 10));
        brandSub.setTextFill(Color.web("#94A3B8"));

        VBox brandBox = new VBox(7, brand, brandSub);
        brandBox.setPadding(new Insets(28, 22, 28, 22));

        Label menuTitle = new Label("MAIN MENU");
        menuTitle.setTextFill(Color.web("#94A3B8"));
        menuTitle.setFont(Font.font("Arial", 10));
        menuTitle.setPadding(new Insets(10, 20, 5, 20));

        Button entryButton = navButton("＋   Vehicle Entry", true);
        Button exitButton = navButton("↗   Vehicle Exit", false);
        Button searchButton = navButton("⌕   Search Vehicle", false);
        Button receiptButton = navButton("▤   Receipts", false);
        Button reportButton = navButton("▥   Daily Report", false);
        Button historyButton = navButton("◷   Parking History", false);
        Button paymentReportButton = navButton("＄   Payment Report", false);

        entryButton.setOnAction(e -> vehicleEntry());
        exitButton.setOnAction(e -> vehicleExit());
        searchButton.setOnAction(e -> searchVehicle());
        receiptButton.setOnAction(e -> showLatestReceipt());
        reportButton.setOnAction(e -> showReport());
        historyButton.setOnAction(e -> showHistory());
        paymentReportButton.setOnAction(e -> showPaymentReport());

        VBox navigation = new VBox(7, menuTitle, entryButton, exitButton, searchButton,
                receiptButton, reportButton, historyButton, paymentReportButton);
        navigation.setPadding(new Insets(5, 12, 15, 12));

        Region sideSpacer = new Region();
        VBox.setVgrow(sideSpacer, Priority.ALWAYS);
        Label sideFooter = new Label("SMART PARKING  •  v1.0");
        sideFooter.setTextFill(Color.web("#64748B"));
        sideFooter.setFont(Font.font("Arial", 9));
        sideFooter.setPadding(new Insets(18));

        VBox sidebar = new VBox(10, brandBox, navigation, sideSpacer, sideFooter);
        sidebar.setPrefWidth(235);
        sidebar.setMinWidth(215);
        sidebar.setStyle("-fx-background-color: #111C35;");

        Label pageTitle = new Label("Parking Overview");
        pageTitle.setFont(Font.font("Arial", 25));
        pageTitle.setTextFill(Color.web("#172033"));
        pageTitle.setStyle("-fx-font-weight: bold;");

        Label pageSubtitle = new Label("Monitor parking availability and manage vehicle activity.");
        pageSubtitle.setFont(Font.font("Arial", 12));
        pageSubtitle.setTextFill(Color.web("#64748B"));

        VBox heading = new VBox(5, pageTitle, pageSubtitle);
        Label dateLabel = new Label(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy")));
        dateLabel.setFont(Font.font("Arial", 12));
        dateLabel.setTextFill(Color.web("#475569"));
        dateLabel.setStyle("-fx-background-color: white; -fx-padding: 11 16; -fx-background-radius: 9; -fx-border-color: #E2E8F0; -fx-border-radius: 9;");
        HBox topBar = new HBox(heading, new Region(), dateLabel);
        HBox.setHgrow(topBar.getChildren().get(1), Priority.ALWAYS);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(28, 32, 20, 32));

        VBox totalCard = createCard("TOTAL SLOTS", "10", "#2563EB");
        VBox availableCard = createCard("AVAILABLE SLOTS", "10", "#16A34A");
        VBox occupiedCard = createCard("OCCUPIED SLOTS", "0", "#DC2626");
        VBox revenueCard = createCard("TOTAL REVENUE", "Rs. 0", "#7C3AED");
        totalValue = (Label) totalCard.getChildren().get(1);
        availableValue = (Label) availableCard.getChildren().get(1);
        occupiedValue = (Label) occupiedCard.getChildren().get(1);
        revenueValue = (Label) revenueCard.getChildren().get(1);

        HBox cards = new HBox(16, totalCard, availableCard, occupiedCard, revenueCard);
        cards.setAlignment(Pos.CENTER);
        cards.setPadding(new Insets(5, 32, 24, 32));
        for (VBox card : List.of(totalCard, availableCard, occupiedCard, revenueCard)) {
            HBox.setHgrow(card, Priority.ALWAYS);
        }

        Label slotTitle = new Label("Live Parking Slots");
        slotTitle.setFont(Font.font("Arial", 19));
        slotTitle.setTextFill(Color.web("#172033"));
        slotTitle.setStyle("-fx-font-weight: bold;");
        Label slotSubtitle = new Label("Real-time view of all 10 parking spaces");
        slotSubtitle.setFont(Font.font("Arial", 11));
        slotSubtitle.setTextFill(Color.web("#64748B"));
        VBox slotHeading = new VBox(5, slotTitle, slotSubtitle);

        Label availableLegend = new Label("● Available");
        availableLegend.setTextFill(Color.web("#15803D"));
        Label occupiedLegend = new Label("● Occupied");
        occupiedLegend.setTextFill(Color.web("#DC2626"));
        HBox legend = new HBox(18, availableLegend, occupiedLegend);
        legend.setAlignment(Pos.CENTER_RIGHT);
        HBox slotTop = new HBox(slotHeading, new Region(), legend);
        HBox.setHgrow(slotTop.getChildren().get(1), Priority.ALWAYS);
        slotTop.setAlignment(Pos.CENTER_LEFT);

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(14);
        grid.setAlignment(Pos.CENTER_LEFT);
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            slotLabels[i] = new Label("Slot " + (i + 1) + "\nAVAILABLE");
            slotLabels[i].setAlignment(Pos.CENTER);
            slotLabels[i].setMinSize(130, 88);
            slotLabels[i].setPrefSize(160, 88);
            slotLabels[i].setMaxSize(Double.MAX_VALUE, 88);
            slotLabels[i].setTextFill(Color.WHITE);
            grid.add(slotLabels[i], i % 5, i / 5);
        }
        for (int c = 0; c < 5; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setHgrow(Priority.ALWAYS);
            cc.setPercentWidth(20);
            grid.getColumnConstraints().add(cc);
        }

        initializeDatabaseSlots();
        loadActiveSlots();
        for (int i = 0; i < TOTAL_SLOTS; i++) updateSlot(i);

        VBox parkingPanel = new VBox(20, slotTop, grid);
        parkingPanel.setPadding(new Insets(24));
        parkingPanel.setStyle("-fx-background-color: white; -fx-background-radius: 15; -fx-border-color: #E2E8F0; -fx-border-radius: 15;");

        Label actionsTitle = new Label("Quick Actions");
        actionsTitle.setFont(Font.font("Arial", 18));
        actionsTitle.setTextFill(Color.web("#172033"));
        actionsTitle.setStyle("-fx-font-weight: bold;");
        Label actionsSub = new Label("Choose an action to manage your parking facility");
        actionsSub.setFont(Font.font("Arial", 11));
        actionsSub.setTextFill(Color.web("#64748B"));
        VBox actionHeading = new VBox(5, actionsTitle, actionsSub);
        FlowPane actionPane = new FlowPane();
        actionPane.setHgap(10);
        actionPane.setVgap(10);
        actionPane.setAlignment(Pos.CENTER_LEFT);
        actionPane.getChildren().addAll(
                actionButton("＋  VEHICLE ENTRY", entryButton),
                actionButton("↗  VEHICLE EXIT", exitButton),
                actionButton("⌕  SEARCH VEHICLE", searchButton),
                actionButton("▤  RECEIPT", receiptButton),
                actionButton("▥  DAILY REPORT", reportButton),
                actionButton("◷  PARKING HISTORY", historyButton),
                actionButton("＄  PAYMENT REPORT", paymentReportButton));
        VBox actionsPanel = new VBox(15, actionHeading, actionPane);
        actionsPanel.setPadding(new Insets(22, 24, 22, 24));
        actionsPanel.setStyle("-fx-background-color: white; -fx-background-radius: 15; -fx-border-color: #E2E8F0; -fx-border-radius: 15;");

       VBox content = new VBox(18, topBar, cards, parkingPanel, actionsPanel);

content.setPadding(new Insets(0, 28, 28, 28));
content.setMinHeight(1100);
content.setFillWidth(true);
content.setStyle("-fx-background-color: #F4F7FB;");

ScrollPane scroll = new ScrollPane();
scroll.setContent(content);
scroll.setFitToWidth(true);
scroll.setFitToHeight(false);
scroll.setPannable(true);

scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

scroll.setStyle(
        "-fx-background: #F4F7FB;" +
        "-fx-background-color: #F4F7FB;" +
        "-fx-border-color: transparent;"
);

HBox root = new HBox(sidebar, scroll);
HBox.setHgrow(scroll, Priority.ALWAYS);

Scene scene = new Scene(root, 1280, 850);

stage.setTitle("ParkSmart | Parking Management");
stage.setScene(scene);
stage.setMinWidth(900);
stage.setMinHeight(550);
stage.setMaximized(true);

stage.show();

updateDashboard();
    }

    private Button navButton(String text, boolean active) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setPadding(new Insets(13, 14, 13, 14));
        button.setFont(Font.font("Arial", 12));
        button.setStyle(active
                ? "-fx-background-color: #2563EB; -fx-text-fill: white; -fx-background-radius: 9; -fx-font-weight: bold; -fx-cursor: hand;"
                : "-fx-background-color: transparent; -fx-text-fill: #CBD5E1; -fx-background-radius: 9; -fx-cursor: hand;");
        return button;
    }

    private Button actionButton(String text, Button source) {
        Button button = new Button(text);
        button.setOnAction(source.getOnAction());
        button.setPadding(new Insets(12, 15, 12, 15));
        button.setFont(Font.font("Arial", 11));
        button.setStyle("-fx-background-color: #EFF6FF; -fx-text-fill: #1D4ED8; -fx-font-weight: bold; -fx-background-radius: 8; -fx-border-color: #DBEAFE; -fx-border-radius: 8; -fx-cursor: hand;");
        button.setOnMouseEntered(e -> button.setStyle("-fx-background-color: #DBEAFE; -fx-text-fill: #1E40AF; -fx-font-weight: bold; -fx-background-radius: 8; -fx-border-color: #BFDBFE; -fx-border-radius: 8; -fx-cursor: hand;"));
        button.setOnMouseExited(e -> button.setStyle("-fx-background-color: #EFF6FF; -fx-text-fill: #1D4ED8; -fx-font-weight: bold; -fx-background-radius: 8; -fx-border-color: #DBEAFE; -fx-border-radius: 8; -fx-cursor: hand;"));
        return button;
    }

    private VBox createCard(String heading, String value, String color) {
        Label name = new Label(heading);
        name.setFont(Font.font("Arial", 11));
        name.setTextFill(Color.web("#64748B"));
        name.setStyle("-fx-font-weight: bold;");
        Label number = new Label(value);
        number.setFont(Font.font("Arial", 27));
        number.setTextFill(Color.web("#172033"));
        number.setStyle("-fx-font-weight: bold;");
        Region accent = new Region();
        accent.setPrefSize(38, 4);
        accent.setMaxSize(38, 4);
        accent.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 3;");
        VBox card = new VBox(11, name, number, accent);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(18, 20, 18, 20));
        card.setMinWidth(150);
        card.setPrefHeight(125);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 13; -fx-border-color: #E2E8F0; -fx-border-radius: 13;");
        return card;
    }

    // VEHICLE ENTRY: payment must be confirmed before a slot is occupied.
    private void vehicleEntry() {
        int freeSlot = findFreeSlot();
        if (freeSlot == -1) {
            showMessage("Parking Full", "No available parking slots!");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Vehicle Entry");
        dialog.setHeaderText("Enter vehicle and parking details");

        ButtonType submit = new ButtonType("Continue to Payment",
                ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(submit, ButtonType.CANCEL);

        TextField numberField = new TextField();
        numberField.setPromptText("Example: AP05AB1234");

        TextField ownerField = new TextField();
        ownerField.setPromptText("Owner name");

        ComboBox<String> typeBox = new ComboBox<>();
        typeBox.getItems().addAll("Car", "Bike", "Scooter", "Other");
        typeBox.setValue("Car");

        Spinner<Integer> hoursSpinner = new Spinner<>(1, 24, 1);
        hoursSpinner.setEditable(true);

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(12);
        form.setPadding(new Insets(15));
        form.addRow(0, new Label("Vehicle Number:"), numberField);
        form.addRow(1, new Label("Owner Name:"), ownerField);
        form.addRow(2, new Label("Vehicle Type:"), typeBox);
        form.addRow(3, new Label("Parking Hours:"), hoursSpinner);
        dialog.getDialogPane().setContent(form);

        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isEmpty() || answer.get() != submit) {
            return;
        }

        String number = numberField.getText().trim().toUpperCase();
        String owner = ownerField.getText().trim();
        String type = typeBox.getValue();
        int hours;

        try {
            hours = Integer.parseInt(hoursSpinner.getEditor().getText().trim());
        } catch (NumberFormatException ex) {
            showMessage("Invalid Duration", "Enter a valid number of parking hours.");
            return;
        }

        if (number.isEmpty() || owner.isEmpty()) {
            showMessage("Missing Details", "Enter both vehicle number and owner name.");
            return;
        }
        if (number.contains(",") || number.contains("|") || number.contains("\n")) {
            showMessage("Invalid Vehicle Number", "Enter a valid vehicle number.");
            return;
        }
        if (hours < 1 || hours > 24) {
            showMessage("Invalid Duration", "Parking duration must be between 1 and 24 hours.");
            return;
        }

        for (String vehicle : vehicles) {
            if (number.equals(vehicle)) {
                showMessage("Duplicate Vehicle", "This vehicle is already parked.");
                return;
            }
        }

        // Check the slot again before asking for payment.
        freeSlot = findFreeSlot();
        if (freeSlot == -1) {
            showMessage("Parking Full", "No available parking slots!");
            return;
        }

        long fee = (long) hours * FEE_PER_HOUR;
        Alert payment = new Alert(Alert.AlertType.CONFIRMATION);
        payment.setTitle("Parking Payment");
        payment.setHeaderText("Advance parking payment");
        payment.setContentText(
                "Vehicle: " + number +
                "\nOwner: " + owner +
                "\nVehicle Type: " + type +
                "\nDuration: " + hours + " hour(s)" +
                "\nRate: Rs. " + FEE_PER_HOUR + " per hour" +
                "\nTotal Amount: Rs. " + fee +
                "\n\nFor this college-project demo, press OK to simulate successful payment."
        );

        Optional<ButtonType> paid = payment.showAndWait();
        if (paid.isEmpty() || paid.get() != ButtonType.OK) {
            showMessage("Payment Cancelled",
                    "Payment was not completed. Vehicle entry was not allowed.");
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        // Store the vehicle only after payment confirmation.
        vehicles[freeSlot] = number;
        owners[freeSlot] = owner;
        vehicleTypes[freeSlot] = type;
        entryTimes[freeSlot] = now;
        bookedHours[freeSlot] = hours;

        if (!saveActiveSlots()) {
            clearSlotData(freeSlot);
            showMessage("Storage Error", "Unable to save vehicle entry.");
            return;
        }

        if (!appendPaymentRecord(number, owner, type, freeSlot + 1,
                now, hours, fee)) {
            clearSlotData(freeSlot);
            saveActiveSlots();
            showMessage("Payment Record Error",
                    "Unable to save the payment record. Vehicle entry was cancelled.");
            return;
        }

        // Save the confirmed entry and payment in MySQL as well.
        if (!saveEntryToDatabase(number, owner, type, freeSlot + 1, now, hours, fee)) {
            clearSlotData(freeSlot);
            saveActiveSlots();
            showMessage("Database Error",
                    "MySQL could not save this entry. Please check the database connection and try again.");
            return;
        }

        updateSlot(freeSlot);
        updateDashboard();

        String receipt = buildEntryReceipt(number, owner, type,
                freeSlot + 1, now, hours, fee);
        saveReceipt(number, receipt);

        showText("Entry Successful - Payment Received",
                receipt + "\nVehicle entry confirmed. Please proceed to Slot "
                        + (freeSlot + 1) + ".");
    }


    // Create the ten parking-slot rows if they do not already exist.
    private void initializeDatabaseSlots() {
        String sql = "INSERT IGNORE INTO parking_slots (slot_id, status) VALUES (?, 'AVAILABLE')";
        try (Connection con = DBConnection.getConnection()) {
            if (con == null) return;
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                for (int i = 1; i <= TOTAL_SLOTS; i++) {
                    ps.setInt(1, i);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        } catch (SQLException e) {
            showMessage("Database Warning", "Could not initialize parking slots in MySQL: " + e.getMessage());
        }
    }

    // Store vehicle, parking history and successful advance payment in one transaction.
    private boolean saveEntryToDatabase(String number, String owner, String type,
                                        int slot, LocalDateTime entry, int hours, long fee) {
        String vehicleSql = "INSERT INTO vehicles (vehicle_number, owner_name, vehicle_type) VALUES (?, ?, ?)";
        String historySql = "INSERT INTO parking_history (vehicle_id, slot_id, entry_time, exit_time, booked_hours, fee) VALUES (?, ?, ?, NULL, ?, ?)";
        String paymentSql = "INSERT INTO payments (history_id, amount, payment_status, payment_time) VALUES (?, ?, 'PAID', ?)";
        String slotSql = "UPDATE parking_slots SET status = 'OCCUPIED' WHERE slot_id = ?";

        try (Connection con = DBConnection.getConnection()) {
            if (con == null) return false;
            con.setAutoCommit(false);
            try (PreparedStatement vehiclePs = con.prepareStatement(vehicleSql, Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement historyPs = con.prepareStatement(historySql, Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement paymentPs = con.prepareStatement(paymentSql);
                 PreparedStatement slotPs = con.prepareStatement(slotSql)) {

                vehiclePs.setString(1, number);
                vehiclePs.setString(2, owner);
                vehiclePs.setString(3, type);
                vehiclePs.executeUpdate();
                try (ResultSet keys = vehiclePs.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("Vehicle ID was not generated");
                    int vehicleId = keys.getInt(1);

                    historyPs.setInt(1, vehicleId);
                    historyPs.setInt(2, slot);
                    historyPs.setTimestamp(3, java.sql.Timestamp.valueOf(entry));
                    historyPs.setInt(4, hours);
                    historyPs.setBigDecimal(5, java.math.BigDecimal.valueOf(fee));
                    historyPs.executeUpdate();
                }
                int historyId;
                try (ResultSet keys = historyPs.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("History ID was not generated");
                    historyId = keys.getInt(1);
                }

                paymentPs.setInt(1, historyId);
                paymentPs.setBigDecimal(2, java.math.BigDecimal.valueOf(fee));
                paymentPs.setTimestamp(3, java.sql.Timestamp.valueOf(entry));
                paymentPs.executeUpdate();

                slotPs.setInt(1, slot);
                if (slotPs.executeUpdate() != 1) throw new SQLException("Parking slot row not found: " + slot);
                con.commit();
                return true;
            } catch (SQLException e) {
                con.rollback();
                System.err.println("MySQL entry save failed: " + e.getMessage());
                return false;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("MySQL connection failed: " + e.getMessage());
            return false;
        }
    }

    // Close the active history row, update the final paid amount and free the slot.
    private boolean saveExitToDatabase(String number, int slot, LocalDateTime exit, long totalPaid) {
        String findSql = "SELECT h.history_id FROM parking_history h JOIN vehicles v ON h.vehicle_id = v.vehicle_id "
                + "WHERE UPPER(v.vehicle_number) = UPPER(?) AND h.slot_id = ? AND h.exit_time IS NULL "
                + "ORDER BY h.history_id DESC LIMIT 1";
        String historySql = "UPDATE parking_history SET exit_time = ?, fee = ? WHERE history_id = ?";
        String paymentSql = "UPDATE payments SET amount = ?, payment_status = 'PAID', payment_time = ? WHERE history_id = ?";
        String slotSql = "UPDATE parking_slots SET status = 'AVAILABLE' WHERE slot_id = ?";

        try (Connection con = DBConnection.getConnection()) {
            if (con == null) return false;
            con.setAutoCommit(false);
            try {
                int historyId;
                try (PreparedStatement findPs = con.prepareStatement(findSql)) {
                    findPs.setString(1, number);
                    findPs.setInt(2, slot);
                    try (ResultSet rs = findPs.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Active parking record not found in MySQL");
                        historyId = rs.getInt("history_id");
                    }
                }
                try (PreparedStatement historyPs = con.prepareStatement(historySql);
                     PreparedStatement paymentPs = con.prepareStatement(paymentSql);
                     PreparedStatement slotPs = con.prepareStatement(slotSql)) {
                    historyPs.setTimestamp(1, java.sql.Timestamp.valueOf(exit));
                    historyPs.setBigDecimal(2, java.math.BigDecimal.valueOf(totalPaid));
                    historyPs.setInt(3, historyId);
                    historyPs.executeUpdate();

                    paymentPs.setBigDecimal(1, java.math.BigDecimal.valueOf(totalPaid));
                    paymentPs.setTimestamp(2, java.sql.Timestamp.valueOf(exit));
                    paymentPs.setInt(3, historyId);
                    paymentPs.executeUpdate();

                    slotPs.setInt(1, slot);
                    slotPs.executeUpdate();
                }
                con.commit();
                return true;
            } catch (SQLException e) {
                con.rollback();
                System.err.println("MySQL exit update failed: " + e.getMessage());
                return false;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("MySQL connection failed: " + e.getMessage());
            return false;
        }
    }

    private int findFreeSlot() {
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            if (vehicles[i] == null) {
                return i;
            }
        }
        return -1;
    }

    private void clearSlotData(int i) {
        vehicles[i] = null;
        owners[i] = null;
        vehicleTypes[i] = null;
        entryTimes[i] = null;
        bookedHours[i] = 0;
    }

    // VEHICLE EXIT: the advance payment is not collected a second time.
    private void vehicleExit() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Vehicle Exit");
        dialog.setHeaderText("Enter Vehicle Registration Number");
        dialog.setContentText("Vehicle Number:");

        dialog.showAndWait().ifPresent(input -> {
            String number = input.trim().toUpperCase();
            if (number.isEmpty()) {
                showMessage("Error", "Enter vehicle number!");
                return;
            }

            for (int i = 0; i < TOTAL_SLOTS; i++) {
                if (number.equals(vehicles[i])) {
                    LocalDateTime exitTime = LocalDateTime.now();
                    long minutes = Math.max(0,
                            Duration.between(entryTimes[i], exitTime).toMinutes());

                    long actualHours = Math.max(1, (minutes + 59) / 60);
                    long paidAmount = (long) bookedHours[i] * FEE_PER_HOUR;
                    long additionalAmount = Math.max(0,
                            actualHours - bookedHours[i]) * FEE_PER_HOUR;

                    String message = "Vehicle: " + number +
                            "\nPaid in advance: Rs. " + paidAmount +
                            "\nActual duration: " + minutes + " minutes" +
                            "\nAdditional amount due: Rs. " + additionalAmount;

                    if (additionalAmount > 0) {
                        Alert extraPayment = new Alert(Alert.AlertType.CONFIRMATION);
                        extraPayment.setTitle("Additional Parking Payment");
                        extraPayment.setHeaderText("Parking duration exceeded");
                        extraPayment.setContentText(message +
                                "\n\nPress OK to simulate successful payment of the additional amount.");
                        Optional<ButtonType> result = extraPayment.showAndWait();
                        if (result.isEmpty() || result.get() != ButtonType.OK) {
                            showMessage("Exit Pending",
                                    "Additional payment is pending. Vehicle slot remains occupied.");
                            return;
                        }
                    }

                    long totalPaid = paidAmount + additionalAmount;
                    String oldVehicle = vehicles[i];
                    String oldOwner = owners[i];
                    String oldType = vehicleTypes[i];
                    LocalDateTime oldEntry = entryTimes[i];
                    int oldBookedHours = bookedHours[i];

                    if (!updatePaymentExit(number, i + 1, exitTime, minutes,
                            actualHours, totalPaid)) {
                        showMessage("Storage Error",
                                "Unable to update the payment record. Exit was not completed.");
                        return;
                    }

                    if (!saveExitToDatabase(number, i + 1, exitTime, totalPaid)) {
                        showMessage("Database Error",
                                "MySQL could not update this vehicle exit. The slot remains occupied.");
                        return;
                    }

                    appendHistory(number, oldOwner, oldType, i + 1,
                            oldEntry, exitTime, minutes, actualHours, totalPaid);

                    clearSlotData(i);
                    if (!saveActiveSlots()) {
                        vehicles[i] = oldVehicle;
                        owners[i] = oldOwner;
                        vehicleTypes[i] = oldType;
                        entryTimes[i] = oldEntry;
                        bookedHours[i] = oldBookedHours;
                        showMessage("Storage Error",
                                "Unable to update active parking slots. Please check the active parking file.");
                        updateSlot(i);
                        updateDashboard();
                        return;
                    }

                    updateSlot(i);
                    updateDashboard();

                    String receipt = buildExitReceipt(number, oldOwner, oldType,
                            i + 1, oldEntry, exitTime, minutes, actualHours, totalPaid);
                    saveReceipt(number, receipt);
                    showText("Vehicle Exit Receipt", receipt);
                    return;
                }
            }

            showMessage("Error", "Vehicle not found!");
        });
    }

    private boolean appendPaymentRecord(String vehicle, String owner, String type,
                                        int slot, LocalDateTime entry,
                                        int hours, long fee) {
        try {
            boolean newFile = !Files.exists(PAYMENT_FILE)
                    || Files.size(PAYMENT_FILE) == 0;
            try (FileWriter writer = new FileWriter(PAYMENT_FILE.toFile(), true)) {
                if (newFile) {
                    writer.write("Vehicle,Owner,Type,Slot,Entry,Exit,Minutes,Hours,Fee,Status\n");
                }
                writer.write(csv(vehicle) + "," + csv(owner) + "," + csv(type) + ","
                        + slot + "," + entry + ",," + 0 + "," + hours + ","
                        + fee + ",PAID\n");
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private boolean updatePaymentExit(String vehicle, int slot,
                                      LocalDateTime exit, long minutes,
                                      long hours, long totalPaid) {
        try {
            if (!Files.exists(PAYMENT_FILE)) return false;
            List<String> lines = new ArrayList<>(Files.readAllLines(PAYMENT_FILE));
            for (int i = lines.size() - 1; i >= 1; i--) {
                String[] d = lines.get(i).split(",", -1);
                if (d.length >= 10 && d[0].equalsIgnoreCase(vehicle)
                        && d[3].equals(String.valueOf(slot)) && d[5].isEmpty()) {
                    d[5] = exit.toString();
                    d[6] = String.valueOf(minutes);
                    d[7] = String.valueOf(hours);
                    d[8] = String.valueOf(totalPaid);
                    d[9] = "PAID";
                    lines.set(i, String.join(",", d));
                    Files.write(PAYMENT_FILE, lines);
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    private String csv(String value) {
        return value.replace(",", " ").replace("\n", " ").replace("\r", " ");
    }

    private void appendHistory(String vehicle, String owner, String type,
                               int slot, LocalDateTime entry, LocalDateTime exit,
                               long minutes, long hours, long fee) {
        String text = "Vehicle: " + vehicle +
                "\nOwner: " + owner +
                "\nType: " + type +
                "\nSlot: " + slot +
                "\nEntry: " + entry.format(FORMAT) +
                "\nExit: " + exit.format(FORMAT) +
                "\nDuration: " + minutes + " minutes" +
                "\nBillable Hours: " + hours +
                "\nTotal Paid: Rs. " + fee +
                "\nPayment Status: PAID\n--------------------------------\n\n";
        try (FileWriter writer = new FileWriter(HISTORY_FILE.toFile(), true)) {
            writer.write(text);
        } catch (IOException e) {
            showMessage("History Warning", "Exit completed, but history could not be saved.");
        }
    }

    private String buildEntryReceipt(String vehicle, String owner, String type,
                                     int slot, LocalDateTime entry,
                                     int hours, long fee) {
        return "================================\n" +
                "       SMART PARKING RECEIPT\n" +
                "================================\n" +
                "Vehicle Number : " + vehicle + "\n" +
                "Owner Name     : " + owner + "\n" +
                "Vehicle Type   : " + type + "\n" +
                "Parking Slot   : " + slot + "\n" +
                "Entry Time     : " + entry.format(FORMAT) + "\n" +
                "Booked Hours   : " + hours + "\n" +
                "Rate Per Hour  : Rs. " + FEE_PER_HOUR + "\n" +
                "TOTAL PAID     : Rs. " + fee + "\n" +
                "PAYMENT STATUS : PAID\n" +
                "EXIT TIME      : Pending\n" +
                "================================\n" +
                "          THANK YOU!\n" +
                "================================\n";
    }

    private String buildExitReceipt(String vehicle, String owner, String type,
                                    int slot, LocalDateTime entry,
                                    LocalDateTime exit, long minutes,
                                    long hours, long fee) {
        return "================================\n" +
                "       SMART PARKING RECEIPT\n" +
                "================================\n" +
                "Vehicle Number : " + vehicle + "\n" +
                "Owner Name     : " + owner + "\n" +
                "Vehicle Type   : " + type + "\n" +
                "Parking Slot   : " + slot + "\n" +
                "Entry Time     : " + entry.format(FORMAT) + "\n" +
                "Exit Time      : " + exit.format(FORMAT) + "\n" +
                "Duration       : " + minutes + " minutes\n" +
                "Billable Hours : " + hours + "\n" +
                "Rate Per Hour  : Rs. " + FEE_PER_HOUR + "\n" +
                "TOTAL PAID     : Rs. " + fee + "\n" +
                "PAYMENT STATUS : PAID\n" +
                "================================\n" +
                "          THANK YOU!\n" +
                "================================\n";
    }

    private void saveReceipt(String vehicle, String receipt) {
        try {
            Files.createDirectories(RECEIPT_FOLDER);
            String safeName = vehicle.replaceAll("[^A-Za-z0-9_-]", "_");
            String fileName = safeName + "_" + System.currentTimeMillis() + ".txt";
            Files.writeString(RECEIPT_FOLDER.resolve(fileName), receipt);
        } catch (IOException e) {
            showMessage("Receipt", "Receipt display is available, but file saving failed.");
        }
    }

    private void showLatestReceipt() {
        try {
            if (!Files.exists(RECEIPT_FOLDER)) {
                showMessage("Receipt", "No receipts available!");
                return;
            }
            List<Path> files;
            try (var stream = Files.list(RECEIPT_FOLDER)) {
                files = stream.filter(p -> p.toString().endsWith(".txt"))
                        .sorted((a, b) -> b.getFileName().toString()
                                .compareTo(a.getFileName().toString()))
                        .toList();
            }
            if (files.isEmpty()) {
                showMessage("Receipt", "No receipts available!");
                return;
            }
            showText("Latest Parking Receipt", Files.readString(files.get(0)));
        } catch (IOException e) {
            showMessage("Error", "Unable to read receipt!");
        }
    }

    private void searchVehicle() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Search Vehicle");
        dialog.setHeaderText("Search Vehicle Details");
        dialog.setContentText("Vehicle Number:");

        dialog.showAndWait().ifPresent(input -> {
            String vehicle = input.trim().toUpperCase();
            if (vehicle.isEmpty()) return;

            StringBuilder result = new StringBuilder();
            boolean found = false;

            for (int i = 0; i < TOTAL_SLOTS; i++) {
                if (vehicle.equals(vehicles[i])) {
                    found = true;
                    result.append("CURRENT PARKING DETAILS\n")
                            .append("------------------------------\n")
                            .append("Vehicle: ").append(vehicle).append("\n")
                            .append("Owner: ").append(owners[i]).append("\n")
                            .append("Type: ").append(vehicleTypes[i]).append("\n")
                            .append("Slot: ").append(i + 1).append("\n")
                            .append("Entry Time: ").append(entryTimes[i].format(FORMAT)).append("\n")
                            .append("Booked Hours: ").append(bookedHours[i]).append("\n")
                            .append("Status: OCCUPIED\n\n");
                }
            }

            try {
                if (Files.exists(PAYMENT_FILE)) {
                    List<String> lines = Files.readAllLines(PAYMENT_FILE);
                    for (int i = 1; i < lines.size(); i++) {
                        String[] d = lines.get(i).split(",", -1);
                        if (d.length >= 10 && d[0].equalsIgnoreCase(vehicle)
                                && !d[5].isEmpty()) {
                            found = true;
                            result.append("PAST PARKING RECORD\n")
                                    .append("------------------------------\n")
                                    .append("Vehicle: ").append(d[0]).append("\n")
                                    .append("Owner: ").append(d[1]).append("\n")
                                    .append("Type: ").append(d[2]).append("\n")
                                    .append("Slot: ").append(d[3]).append("\n")
                                    .append("Entry: ").append(d[4]).append("\n")
                                    .append("Exit: ").append(d[5]).append("\n")
                                    .append("Duration: ").append(d[6]).append(" minutes\n")
                                    .append("Fee: Rs. ").append(d[8]).append("\n")
                                    .append("Payment: ").append(d[9]).append("\n\n");
                        }
                    }
                }
            } catch (IOException e) {
                showMessage("Error", "Unable to search records!");
                return;
            }

            if (found) showText("Vehicle Search Result", result.toString());
            else showMessage("Search", "Vehicle not found!");
        });
    }

    private void updateSlot(int i) {
        if (slotLabels[i] == null) {
            return;
        }

        Color slotColor;
        if (vehicles[i] == null) {
            slotLabels[i].setText("PARKING SLOT " + (i + 1) + "\n\nAVAILABLE");
            slotColor = Color.web("#16A34A");
        } else {
            slotLabels[i].setText("PARKING SLOT " + (i + 1) + "\n" +
                    vehicles[i] + "\nOCCUPIED");
            slotColor = Color.web("#DC2626");
        }
        slotLabels[i].setBackground(new Background(
                new BackgroundFill(slotColor, new CornerRadii(11), Insets.EMPTY)));
        slotLabels[i].setTextFill(Color.WHITE);
        slotLabels[i].setAlignment(Pos.CENTER);
        slotLabels[i].setMinSize(130, 88);
        slotLabels[i].setPrefSize(160, 88);
        slotLabels[i].setMaxSize(Double.MAX_VALUE, 88);
        slotLabels[i].setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: white; -fx-padding: 8;");
    }

    private void updateDashboard() {
        int occupied = 0;
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            if (vehicles[i] != null) occupied++;
        }
        totalValue.setText(String.valueOf(TOTAL_SLOTS));
        occupiedValue.setText(String.valueOf(occupied));
        availableValue.setText(String.valueOf(TOTAL_SLOTS - occupied));
        revenueValue.setText("Rs. " + getTotalRevenue());
    }

    private boolean saveActiveSlots() {
        try {
            List<String> data = new ArrayList<>();
            for (int i = 0; i < TOTAL_SLOTS; i++) {
                if (vehicles[i] != null) {
                    data.add((i + 1) + "|" + vehicles[i] + "|" +
                            entryTimes[i] + "|" + owners[i] + "|" +
                            vehicleTypes[i] + "|" + bookedHours[i]);
                }
            }
            Files.write(ACTIVE_FILE, data);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void loadActiveSlots() {
        try {
            if (!Files.exists(ACTIVE_FILE)) return;
            for (String line : Files.readAllLines(ACTIVE_FILE)) {
                String[] data = line.split("\\|", -1);
                if (data.length < 3) continue;

                int slot = Integer.parseInt(data[0]) - 1;
                if (slot >= 0 && slot < TOTAL_SLOTS && vehicles[slot] == null) {
                    vehicles[slot] = data[1];
                    entryTimes[slot] = LocalDateTime.parse(data[2]);
                    owners[slot] = data.length > 3 ? data[3] : "Unknown";
                    vehicleTypes[slot] = data.length > 4 ? data[4] : "Unknown";
                    bookedHours[slot] = data.length > 5
                            ? Integer.parseInt(data[5]) : 1;
                    updateSlot(slot);
                }
            }
        } catch (Exception e) {
            showMessage("Storage Error", "Unable to load saved parking records!");
        }
    }

    private long getTotalRevenue() {
        long revenue = 0;
        try {
            if (!Files.exists(PAYMENT_FILE)) return 0;
            List<String> lines = Files.readAllLines(PAYMENT_FILE);
            for (int i = 1; i < lines.size(); i++) {
                String[] d = lines.get(i).split(",", -1);
                if (d.length >= 10 && d[9].equals("PAID")) {
                    revenue += Long.parseLong(d[8]);
                }
            }
        } catch (Exception e) {
            return revenue;
        }
        return revenue;
    }

    private long getTodayRevenue() {
        long revenue = 0;
        try {
            if (!Files.exists(PAYMENT_FILE)) return 0;
            List<String> lines = Files.readAllLines(PAYMENT_FILE);
            for (int i = 1; i < lines.size(); i++) {
                String[] d = lines.get(i).split(",", -1);
                if (d.length >= 10 && d[9].equals("PAID")) {
                    LocalDate date = LocalDateTime.parse(d[4]).toLocalDate();
                    if (date.equals(LocalDate.now())) {
                        revenue += Long.parseLong(d[8]);
                    }
                }
            }
        } catch (Exception e) {
            return revenue;
        }
        return revenue;
    }

    private int getUnpaidCount() {
        // Entry is allowed only after payment confirmation in this version.
        return 0;
    }

    private void showHistory() {
        try {
            if (!Files.exists(HISTORY_FILE)) {
                showMessage("History", "No parking history available!");
                return;
            }
            showText("Parking History", Files.readString(HISTORY_FILE));
        } catch (IOException e) {
            showMessage("Error", "Unable to read parking history!");
        }
    }

    private void showReport() {
        int occupied = 0;
        StringBuilder report = new StringBuilder("SMART PARKING DAILY REPORT\n\n");

        for (int i = 0; i < TOTAL_SLOTS; i++) {
            if (vehicles[i] != null) {
                occupied++;
                report.append("Slot ").append(i + 1).append(" : ")
                        .append(vehicles[i]).append(" (OCCUPIED)\n");
            } else {
                report.append("Slot ").append(i + 1).append(" : AVAILABLE\n");
            }
        }

        report.append("\nTotal Slots: ").append(TOTAL_SLOTS)
                .append("\nOccupied: ").append(occupied)
                .append("\nAvailable: ").append(TOTAL_SLOTS - occupied)
                .append("\n\nToday's Collection: Rs. ").append(getTodayRevenue())
                .append("\nTotal Revenue: Rs. ").append(getTotalRevenue())
                .append("\nUnpaid Transactions: ").append(getUnpaidCount());

        showText("Daily Report", report.toString());
    }

    private void showPaymentReport() {
        StringBuilder report = new StringBuilder("PARKING PAYMENT REPORT\n\n");
        report.append("Total Paid Revenue: Rs. ").append(getTotalRevenue())
                .append("\n\nToday's Collection: Rs. ").append(getTodayRevenue())
                .append("\n\nUnpaid Transactions: ").append(getUnpaidCount())
                .append("\n\nParking Fee: Rs. ").append(FEE_PER_HOUR)
                .append(" per hour\n")
                .append("\nPayment is collected before vehicle entry.");
        showText("Payment Report", report.toString());
    }

    private void showText(String title, String message) {
        TextArea area = new TextArea(message);
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefSize(650, 450);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.getDialogPane().setContent(area);
        alert.getDialogPane().setPrefWidth(700);
        alert.showAndWait();
    }

    private void showMessage(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}