package com.pdfreader.ui;

import com.pdfreader.config.ConfigManager;
import com.pdfreader.model.Book;
import com.pdfreader.service.BookService;
import com.pdfreader.service.DriveService;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main application window layout.
 * Contains the dual-panel library view (Local Books + Google Drive Books)
 * and switches seamlessly to the embedded PdfReaderView when reading.
 */
public class MainView extends StackPane {

    private final BookService bookService;
    private final DriveService driveService;
    private final Stage primaryStage;

    // Thumbnail cache and background loader
    private final Map<String, Image> fxThumbCache = new ConcurrentHashMap<>();
    private final Map<String, Image> driveThumbCache = new ConcurrentHashMap<>();
    private final ExecutorService thumbExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "thumb-loader");
        t.setDaemon(true);
        return t;
    });

    // View switcher components
    private BorderPane libraryViewPane;
    private PdfReaderView pdfReaderView;

    // Main layout panels & collapsible sidebar
    private StackPane contentStack;
    private VBox sidebar;
    private VBox homePanel;
    private VBox localPanel;
    private VBox drivePanel;
    private Button navHomeBtn;
    private Button navLocalBtn;
    private Button navDriveBtn;
    private Label sidebarAuthStatusLabel;
    private Label sidebarDriveFolderLabel;
    private Label homeLocalCountLabel;
    private Label homeDriveCountLabel;
    private Label homeAuthStatusLabel;
    private Label homeDriveFolderLabel;

    // Local library controls
    private TableView<Book> localTableView;
    private FlowPane localIconsFlowPane;
    private ScrollPane localIconsScrollPane;
    private StackPane localViewSwitcherStack;
    private boolean isLocalIconsView = false;
    private ObservableList<Book> localBooksMasterList;
    private FilteredList<Book> localFilteredList;
    private TextField localSearchField;
    private Label localCountLabel;
    private Button openLocalBtn;
    private Button uploadLocalBtn;
    private Button renameLocalBtn;
    private Button deleteLocalBtn;
    private Book selectedBook = null;
    private final Set<Book> selectedLocalBooks = new LinkedHashSet<>();
    private boolean syncingLocalSelection = false;

    // Selected book preview controls
    private ImageView previewCover;
    private Label previewTitle;
    private Label previewDetails;

    // Drive library controls
    private TableView<Book> driveTableView;
    private FlowPane driveIconsFlowPane;
    private ScrollPane driveIconsScrollPane;
    private StackPane driveViewSwitcherStack;
    private boolean isDriveIconsView = false;
    private Label driveCountLabel;
    private MenuButton driveViewModeMenuBtn;
    private Book selectedDriveBook = null;
    private ObservableList<Book> driveBooksMasterList;
    private FilteredList<Book> driveFilteredList;
    private TextField driveSearchField;
    private Label driveStatusBadge;
    private Button readOnlineDriveBtn;
    private Button downloadDriveBtn;
    private Button refreshDriveBtn;
    private Button renameDriveBtn;
    private Button deleteDriveBtn;
    private Button unlinkFolderBtn;
    private Button summaryUnlinkBtn;
    private TextField sharedFolderField;
    private Label autoDetectedRoleBadge;
    private VBox sharedFolderCard;
    private HBox folderSummaryBar;
    private Label summaryFolderIdLabel;
    private Label summaryRoleBadge;
    private final Set<Book> selectedDriveBooks = new LinkedHashSet<>();
    private boolean syncingDriveSelection = false;

    // Status bar controls
    private Label statusMessageLabel;
    private ProgressBar taskProgressBar;

    public MainView(Stage primaryStage, BookService bookService, DriveService driveService) {
        this.primaryStage = primaryStage;
        this.bookService = bookService;
        this.driveService = driveService;

        initViews();
        loadLocalBooks();
        loadDriveBooks();
        updateDriveStatusUI();
    }

    private void initViews() {
        // --- Layer 1: Library View ---
        libraryViewPane = new BorderPane();
        libraryViewPane.getStyleClass().add("main-container");

        // Top App Bar
        HBox topBar = createTopBar();
        libraryViewPane.setTop(topBar);

        homePanel = createHomePanel();
        localPanel = createLocalLibraryPanel();
        drivePanel = createDriveLibraryPanel();

        contentStack = new StackPane(homePanel, localPanel, drivePanel);
        contentStack.getStyleClass().add("content-stack");
        libraryViewPane.setLeft(createSidebar());
        libraryViewPane.setCenter(contentStack);
        showSection("HOME");

        // Bottom: Global Status Bar
        HBox statusBar = createStatusBar();
        libraryViewPane.setBottom(statusBar);

        // --- Layer 2: Reader View ---
        pdfReaderView = new PdfReaderView(this::showLibraryView);
        pdfReaderView.setVisible(false);

        getChildren().addAll(libraryViewPane, pdfReaderView);
    }

    private HBox createTopBar() {
        HBox topBar = new HBox(12);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(10, 16, 10, 16));
        topBar.getStyleClass().add("top-bar");

        Label appTitle = new Label("PDF Book Reader");
        appTitle.setGraphic(AppIcons.createOpenBookIcon(20, Color.web("#2563eb")));
        appTitle.getStyleClass().add("app-title");

        Label badge = new Label("Library Manager");
        badge.getStyleClass().add("version-badge");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refreshAllBtn = new Button("Refresh Libraries");
        refreshAllBtn.setGraphic(AppIcons.createRefreshIcon(14));
        refreshAllBtn.getStyleClass().addAll("btn", "btn-secondary");
        refreshAllBtn.setOnAction(e -> {
            loadLocalBooks();
            loadDriveBooks();
        });

        topBar.getChildren().addAll(appTitle, badge, spacer, refreshAllBtn);
        return topBar;
    }

    private VBox createSidebar() {
        sidebar = new VBox(12);
        sidebar.setPadding(new Insets(16, 12, 16, 12));
        sidebar.getStyleClass().add("app-sidebar");
        sidebar.setPrefWidth(224);

        Label navTitle = new Label("Navigation");
        navTitle.getStyleClass().add("sidebar-section-title");

        navHomeBtn = createSidebarNavButton("Home", AppIcons.createHomeIcon(15), () -> showSection("HOME"));
        navLocalBtn = createSidebarNavButton("Local Library", AppIcons.createFolderIcon(15), () -> showSection("LOCAL"));
        navDriveBtn = createSidebarNavButton("Drive Library", new DriveIcon(15), () -> showSection("DRIVE"));

        Label driveTitle = new Label("Google Drive");
        driveTitle.getStyleClass().add("sidebar-section-title");

        sidebarAuthStatusLabel = new Label();
        sidebarAuthStatusLabel.getStyleClass().add("sidebar-status-label");
        sidebarAuthStatusLabel.setWrapText(true);

        sidebarDriveFolderLabel = new Label();
        sidebarDriveFolderLabel.getStyleClass().add("sidebar-muted-label");
        sidebarDriveFolderLabel.setWrapText(true);

        Button accountBtn = new Button("Google Account");
        accountBtn.setGraphic(AppIcons.createAccountIcon(14));
        accountBtn.getStyleClass().addAll("btn", "btn-primary", "sidebar-action-btn");
        accountBtn.setMaxWidth(Double.MAX_VALUE);
        accountBtn.setOnAction(e -> handleOpenDriveAuthDialog());

        Button importBtn = new Button("Import Local PDF");
        importBtn.setGraphic(AppIcons.createDownloadIcon(14, Color.web("#4a5568")));
        importBtn.getStyleClass().addAll("btn", "btn-secondary", "sidebar-action-btn");
        importBtn.setMaxWidth(Double.MAX_VALUE);
        importBtn.setOnAction(e -> {
            showSection("LOCAL");
            handleImportLocalPdf();
        });

        Button folderBtn = new Button("Open Books Folder");
        folderBtn.setGraphic(AppIcons.createFolderIcon(14));
        folderBtn.getStyleClass().addAll("btn", "btn-secondary", "sidebar-action-btn");
        folderBtn.setMaxWidth(Double.MAX_VALUE);
        folderBtn.setOnAction(e -> handleOpenBooksFolderInExplorer());

        Button driveBrowserBtn = new Button("Open Drive Folder");
        driveBrowserBtn.setGraphic(new DriveIcon(14));
        driveBrowserBtn.getStyleClass().addAll("btn", "btn-secondary", "sidebar-action-btn");
        driveBrowserBtn.setMaxWidth(Double.MAX_VALUE);
        driveBrowserBtn.setOnAction(e -> handleOpenDriveFolderInBrowser());

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        sidebar.getChildren().addAll(
                navTitle, navHomeBtn, navLocalBtn, navDriveBtn,
                new Separator(),
                driveTitle, sidebarAuthStatusLabel, sidebarDriveFolderLabel, accountBtn,
                new Separator(),
                importBtn, folderBtn, driveBrowserBtn,
                spacer
        );
        updateGoogleAuthUI();
        return sidebar;
    }

    private Button createSidebarNavButton(String text, Node icon, Runnable action) {
        Button button = new Button(text);
        button.setGraphic(icon);
        button.getStyleClass().add("sidebar-nav-btn");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setOnAction(e -> action.run());
        return button;
    }

    private VBox createHomePanel() {
        VBox panel = new VBox(16);
        panel.setPadding(new Insets(24));
        panel.getStyleClass().addAll("library-panel", "home-panel");

        Label title = new Label("Home");
        title.getStyleClass().add("home-title");
        Label subtitle = new Label("Manage local PDFs and Google Drive books from a cleaner workspace.");
        subtitle.getStyleClass().add("home-subtitle");

        HBox cards = new HBox(12);
        cards.getStyleClass().add("home-card-row");
        homeLocalCountLabel = new Label("0 books");
        homeDriveCountLabel = new Label("0 books");
        homeAuthStatusLabel = new Label("Not signed in");
        homeDriveFolderLabel = new Label("No Drive folder linked");
        cards.getChildren().addAll(
                createHomeCard("Local Library", homeLocalCountLabel, AppIcons.createFolderIcon(20), () -> showSection("LOCAL")),
                createHomeCard("Drive Library", homeDriveCountLabel, new DriveIcon(20), () -> showSection("DRIVE")),
                createHomeCard("Google Account", homeAuthStatusLabel, AppIcons.createAccountIcon(20), this::handleOpenDriveAuthDialog)
        );

        HBox quickActions = new HBox(10);
        quickActions.setAlignment(Pos.CENTER_LEFT);
        Button openLocal = new Button("Open Local Library");
        openLocal.setGraphic(AppIcons.createFolderIcon(14));
        openLocal.getStyleClass().addAll("btn", "btn-primary");
        openLocal.setOnAction(e -> showSection("LOCAL"));

        Button openDrive = new Button("Open Drive Library");
        openDrive.setGraphic(new DriveIcon(14));
        openDrive.getStyleClass().addAll("btn", "btn-secondary");
        openDrive.setOnAction(e -> showSection("DRIVE"));

        Button signIn = new Button("Google Sign-In");
        signIn.setGraphic(AppIcons.createAccountIcon(14));
        signIn.getStyleClass().addAll("btn", "btn-secondary");
        signIn.setOnAction(e -> handleOpenDriveAuthDialog());
        quickActions.getChildren().addAll(openLocal, openDrive, signIn);

        HBox driveSummary = new HBox(8);
        driveSummary.setAlignment(Pos.CENTER_LEFT);
        driveSummary.getStyleClass().add("home-drive-summary");
        Label driveSummaryTitle = new Label("Drive Folder");
        driveSummaryTitle.getStyleClass().add("shared-link-title");
        homeDriveFolderLabel.getStyleClass().add("shared-link-hint");
        driveSummary.getChildren().addAll(driveSummaryTitle, homeDriveFolderLabel);

        panel.getChildren().addAll(title, subtitle, cards, driveSummary, quickActions);
        return panel;
    }

    private VBox createHomeCard(String title, Label valueLabel, Node icon, Runnable action) {
        VBox card = new VBox(8);
        card.getStyleClass().add("home-card");
        card.setPrefWidth(220);
        HBox heading = new HBox(8);
        heading.setAlignment(Pos.CENTER_LEFT);
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("home-card-title");
        heading.getChildren().addAll(icon, titleLabel);
        valueLabel.getStyleClass().add("home-card-value");
        card.getChildren().addAll(heading, valueLabel);
        card.setOnMouseClicked(e -> action.run());
        return card;
    }

    private void showSection(String section) {
        boolean homeVisible = "HOME".equals(section);
        boolean localVisible = "LOCAL".equals(section);
        boolean driveVisible = "DRIVE".equals(section);

        setPanelVisible(homePanel, homeVisible);
        setPanelVisible(localPanel, localVisible);
        setPanelVisible(drivePanel, driveVisible);

        updateNavButtonState(navHomeBtn, homeVisible);
        updateNavButtonState(navLocalBtn, localVisible);
        updateNavButtonState(navDriveBtn, driveVisible);
    }

    private void setPanelVisible(Node panel, boolean visible) {
        if (panel == null) return;
        panel.setVisible(visible);
        panel.setManaged(visible);
        panel.setOpacity(visible ? 1.0 : 0.0);
    }

    private void updateNavButtonState(Button button, boolean selected) {
        if (button == null) return;
        if (selected) {
            if (!button.getStyleClass().contains("sidebar-nav-selected")) {
                button.getStyleClass().add("sidebar-nav-selected");
            }
        } else {
            button.getStyleClass().remove("sidebar-nav-selected");
        }
    }

    private void updateGoogleAuthUI() {
        boolean signedIn = driveService.isAuthenticated();
        String authText = signedIn ? "Signed in: " + driveService.getUserEmail() : "Not signed in";
        if (sidebarAuthStatusLabel != null) {
            sidebarAuthStatusLabel.setText(authText);
            sidebarAuthStatusLabel.getStyleClass().setAll("sidebar-status-label", signedIn ? "signed-in" : "signed-out");
        }
        if (homeAuthStatusLabel != null) {
            homeAuthStatusLabel.setText(authText);
        }

        String folderUrl = driveService.getSharedFolderUrl();
        String folderText = (folderUrl == null || folderUrl.isBlank())
                ? "No Drive folder linked"
                : "Folder linked";
        if (sidebarDriveFolderLabel != null) {
            sidebarDriveFolderLabel.setText(folderText);
        }
        if (homeDriveFolderLabel != null) {
            homeDriveFolderLabel.setText(folderText);
        }
    }

    private void handleOpenDriveAuthDialog() {
        CredentialsDialog dialog = new CredentialsDialog(driveService, primaryStage, () -> {
            updateGoogleAuthUI();
            updateDriveStatusUI();
            loadDriveBooks();
            setStatus(driveService.isAuthenticated()
                    ? "Signed in to Google Drive as " + driveService.getUserEmail()
                    : "Signed out of Google Drive.");
        });
        dialog.showAndWait();
        updateGoogleAuthUI();
        updateDriveStatusUI();
    }

    private void showSignInRequired(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Sign In Required");
        alert.setHeaderText("Google Drive sign-in required");
        alert.setContentText(message + "\n\nSign in with your Google account to manage Drive files from inside the app.");
        ButtonType signInType = new ButtonType("Sign In");
        alert.getButtonTypes().setAll(signInType, ButtonType.CANCEL);
        alert.showAndWait().ifPresent(response -> {
            if (response == signInType) {
                handleOpenDriveAuthDialog();
            }
        });
    }

    private VBox createLocalLibraryPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(14));
        panel.getStyleClass().add("library-panel");

        // Header
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Local Books Library");
        title.setGraphic(AppIcons.createFolderIcon(18));
        title.getStyleClass().add("panel-title");

        localCountLabel = new Label("0 books");
        localCountLabel.getStyleClass().add("count-badge");

        // View Mode Switcher Dropdown (Details vs Icons)
        MenuButton viewModeMenuBtn = new MenuButton("View: Details");
        viewModeMenuBtn.setGraphic(AppIcons.createDetailsIcon(14));
        viewModeMenuBtn.getStyleClass().addAll("btn", "btn-secondary", "btn-sm", "dropdown-menu-btn");

        ToggleGroup viewModeGroup = new ToggleGroup();
        RadioMenuItem detailsItem = new RadioMenuItem("Details");
        detailsItem.setGraphic(AppIcons.createDetailsIcon(14));
        detailsItem.setToggleGroup(viewModeGroup);
        detailsItem.setSelected(true);

        RadioMenuItem iconsItem = new RadioMenuItem("Icons");
        iconsItem.setGraphic(AppIcons.createGridIcon(14));
        iconsItem.setToggleGroup(viewModeGroup);

        viewModeMenuBtn.getItems().addAll(detailsItem, iconsItem);

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        header.getChildren().addAll(title, localCountLabel, headerSpacer, viewModeMenuBtn);

        // Search Field
        localSearchField = new TextField();
        localSearchField.setPromptText("Search local books by title...");
        localSearchField.getStyleClass().add("search-field");

        // Table
        localBooksMasterList = FXCollections.observableArrayList();
        localFilteredList = new FilteredList<>(localBooksMasterList, p -> true);
        localSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            localFilteredList.setPredicate(book -> {
                if (newVal == null || newVal.isBlank()) return true;
                return book.getTitle().toLowerCase().contains(newVal.toLowerCase().trim());
            });
            updateLocalCount();
            if (isLocalIconsView) {
                updateLocalIconsGrid();
            }
        });

        localTableView = new TableView<>(localFilteredList);
        localTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        localTableView.setPlaceholder(new Label("No PDF books found in your 'Books' folder.\nClick 'Import PDF...' to add books."));
        localTableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        VBox.setVgrow(localTableView, Priority.ALWAYS);

        // Book Cover Thumbnail column
        TableColumn<Book, Book> coverCol = new TableColumn<>("Cover");
        coverCol.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue()));
        coverCol.setPrefWidth(52);
        coverCol.setMinWidth(52);
        coverCol.setMaxWidth(60);
        coverCol.setResizable(false);
        coverCol.setCellFactory(col -> new TableCell<Book, Book>() {
            private final ImageView imageView = new ImageView();
            private final StackPane placeholder = AppIcons.createBookCoverPlaceholder(32, 44);
            private final StackPane container = new StackPane(placeholder, imageView);

            {
                imageView.setFitWidth(32);
                imageView.setFitHeight(44);
                imageView.setPreserveRatio(true);
                imageView.setSmooth(true);
                imageView.getStyleClass().add("book-thumb-image");
                container.setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(Book book, boolean empty) {
                super.updateItem(book, empty);
                if (empty || book == null) {
                    setGraphic(null);
                } else {
                    imageView.setImage(null);
                    placeholder.setVisible(true);
                    setGraphic(container);
                    loadThumbnailAsync(book, imageView, placeholder);
                }
            }
        });

        TableColumn<Book, String> nameCol = new TableColumn<>("Title");
        nameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getTitle()));
        nameCol.setPrefWidth(220);

        TableColumn<Book, String> sizeCol = new TableColumn<>("Size");
        sizeCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFormattedFileSize()));
        sizeCol.setPrefWidth(80);

        TableColumn<Book, String> dateCol = new TableColumn<>("Modified");
        dateCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFormattedLastModified()));
        dateCol.setPrefWidth(120);

        localTableView.getColumns().addAll(coverCol, nameCol, sizeCol, dateCol);

        // Double-click to open
        localTableView.setRowFactory(tv -> {
            TableRow<Book> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    openBookInReader(row.getItem());
                }
            });
            return row;
        });

        // Icons / Bookshelf FlowPane view
        localIconsFlowPane = new FlowPane();
        localIconsFlowPane.setHgap(16);
        localIconsFlowPane.setVgap(16);
        localIconsFlowPane.setPadding(new Insets(10));
        localIconsFlowPane.getStyleClass().add("bookshelf-grid");

        localIconsScrollPane = new ScrollPane(localIconsFlowPane);
        localIconsScrollPane.setFitToWidth(true);
        localIconsScrollPane.setFitToHeight(true);
        localIconsScrollPane.getStyleClass().add("bookshelf-scroll-pane");
        localIconsScrollPane.setVisible(false);
        localIconsScrollPane.setManaged(false);
        VBox.setVgrow(localIconsScrollPane, Priority.ALWAYS);

        localViewSwitcherStack = new StackPane(localTableView, localIconsScrollPane);
        VBox.setVgrow(localViewSwitcherStack, Priority.ALWAYS);

        detailsItem.setOnAction(e -> {
            isLocalIconsView = false;
            localTableView.setVisible(true);
            localTableView.setManaged(true);
            localIconsScrollPane.setVisible(false);
            localIconsScrollPane.setManaged(false);
            viewModeMenuBtn.setText("View: Details");
            viewModeMenuBtn.setGraphic(AppIcons.createDetailsIcon(14));
            syncLocalSelectionToTable();
        });

        iconsItem.setOnAction(e -> {
            isLocalIconsView = true;
            localTableView.setVisible(false);
            localTableView.setManaged(false);
            localIconsScrollPane.setVisible(true);
            localIconsScrollPane.setManaged(true);
            viewModeMenuBtn.setText("View: Icons");
            viewModeMenuBtn.setGraphic(AppIcons.createGridIcon(14));
            updateLocalIconsGrid();
        });

        // Selected Book Preview Bar
        previewCover = new ImageView();
        previewCover.setFitWidth(42);
        previewCover.setFitHeight(58);
        previewCover.setPreserveRatio(true);
        previewCover.setSmooth(true);
        previewCover.getStyleClass().add("preview-cover-thumb");

        previewTitle = new Label("Select a book to preview & read");
        previewTitle.getStyleClass().add("preview-title");
        previewTitle.setMaxWidth(300);

        previewDetails = new Label("Double-click a book or click 'Open Book'");
        previewDetails.getStyleClass().add("preview-details");

        VBox previewText = new VBox(2, previewTitle, previewDetails);
        HBox.setHgrow(previewText, Priority.ALWAYS);

        HBox previewCard = new HBox(12, previewCover, previewText);
        previewCard.setAlignment(Pos.CENTER_LEFT);
        previewCard.getStyleClass().add("selected-book-card");

        localTableView.getSelectionModel().getSelectedItems().addListener((ListChangeListener<Book>) change -> {
            if (!syncingLocalSelection) {
                selectedLocalBooks.clear();
                selectedLocalBooks.addAll(localTableView.getSelectionModel().getSelectedItems());
                selectedBook = localTableView.getSelectionModel().getSelectedItem();
                updateLocalSelectionUI();
            }
        });

        // Bottom Action Buttons
        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_LEFT);

        openLocalBtn = new Button("Open Book");
        openLocalBtn.setGraphic(AppIcons.createOpenBookIcon(14, Color.WHITE));
        openLocalBtn.getStyleClass().addAll("btn", "btn-primary");
        openLocalBtn.setOnAction(e -> {
            Book selected = getSelectedBook();
            if (selected != null) {
                openBookInReader(selected);
            } else {
                showInfo("Select a Book", "Please select a book from the local list to open.");
            }
        });

        uploadLocalBtn = new Button("Upload to Drive");
        uploadLocalBtn.setGraphic(new DriveIcon(14));
        uploadLocalBtn.setText("");
        uploadLocalBtn.getStyleClass().addAll("btn", "btn-secondary", "icon-btn");
        uploadLocalBtn.setOnAction(e -> handleUploadSelectedBook());

        renameLocalBtn = new Button("Rename");
        renameLocalBtn.setGraphic(AppIcons.createPenIcon(14, Color.web("#4a5568")));
        renameLocalBtn.setText("");
        renameLocalBtn.getStyleClass().addAll("btn", "btn-secondary", "icon-btn");
        renameLocalBtn.setOnAction(e -> handleRenameSelectedLocalBook());

        deleteLocalBtn = new Button("Delete");
        deleteLocalBtn.setGraphic(AppIcons.createTrashIcon(14));
        deleteLocalBtn.setText("");
        deleteLocalBtn.getStyleClass().addAll("btn", "btn-danger", "icon-btn");
        deleteLocalBtn.setOnAction(e -> handleDeleteSelectedLocalBook());

        actions.getChildren().addAll(openLocalBtn, uploadLocalBtn, renameLocalBtn, deleteLocalBtn);

        panel.getChildren().addAll(header, localSearchField, localViewSwitcherStack, previewCard, actions);
        return panel;
    }

    private VBox createDriveLibraryPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(14));
        panel.getStyleClass().add("library-panel");

        // Header
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);

        DriveIcon headerDriveIcon = new DriveIcon(22);
        Label title = new Label("Google Drive Shared Library");
        title.getStyleClass().add("panel-title");

        driveStatusBadge = new Label("No Folder Linked");
        driveStatusBadge.getStyleClass().addAll("status-badge", "status-badge-disconnected");

        driveCountLabel = new Label("0 books");
        driveCountLabel.getStyleClass().add("count-badge");

        // Drive View Mode Switcher Dropdown (Details vs Icons)
        driveViewModeMenuBtn = new MenuButton("View: Details");
        driveViewModeMenuBtn.setGraphic(AppIcons.createDetailsIcon(14));
        driveViewModeMenuBtn.getStyleClass().addAll("btn", "btn-secondary", "btn-sm", "dropdown-menu-btn");

        ToggleGroup driveViewModeGroup = new ToggleGroup();
        RadioMenuItem driveDetailsItem = new RadioMenuItem("Details");
        driveDetailsItem.setGraphic(AppIcons.createDetailsIcon(14));
        driveDetailsItem.setToggleGroup(driveViewModeGroup);
        driveDetailsItem.setSelected(true);

        RadioMenuItem driveIconsItem = new RadioMenuItem("Icons");
        driveIconsItem.setGraphic(AppIcons.createGridIcon(14));
        driveIconsItem.setToggleGroup(driveViewModeGroup);

        driveViewModeMenuBtn.getItems().addAll(driveDetailsItem, driveIconsItem);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        header.getChildren().addAll(headerDriveIcon, title, driveStatusBadge, driveCountLabel, spacer, driveViewModeMenuBtn);

        // Google Drive Shared Folder Configuration Card
        sharedFolderCard = new VBox(8);
        sharedFolderCard.getStyleClass().add("shared-link-card");

        HBox cardHeaderRow = new HBox(8);
        cardHeaderRow.setAlignment(Pos.CENTER_LEFT);
        Label folderTitle = new Label("Shared Folder Configuration");
        folderTitle.setGraphic(AppIcons.createFolderIcon(16));
        folderTitle.getStyleClass().add("shared-link-title");

        autoDetectedRoleBadge = new Label("Auto-detected: Editor");
        autoDetectedRoleBadge.getStyleClass().addAll("status-badge", "status-badge-editor");
        autoDetectedRoleBadge.setVisible(driveService.hasSharedFolder());
        autoDetectedRoleBadge.setManaged(driveService.hasSharedFolder());
        autoDetectedRoleBadge.setCursor(Cursor.HAND);
        autoDetectedRoleBadge.setTooltip(new Tooltip("Auto-detected permission. Click to cycle / override role manually (Editor -> Viewer)."));
        autoDetectedRoleBadge.setOnMouseClicked(e -> cycleFolderPermission());

        Region cardSpacer = new Region();
        HBox.setHgrow(cardSpacer, Priority.ALWAYS);

        Button collapseCardBtn = new Button("Hide Box");
        collapseCardBtn.setGraphic(AppIcons.createChevronUpIcon(12));
        collapseCardBtn.getStyleClass().addAll("btn", "btn-secondary", "btn-sm");
        collapseCardBtn.setTooltip(new Tooltip("Hide this configuration box to maximize reading/table space"));
        collapseCardBtn.setOnAction(e -> {
            ConfigManager.getInstance().getConfig().setDriveCardCollapsed(true);
            ConfigManager.getInstance().saveConfig();
            updateDriveStatusUI();
        });

        cardHeaderRow.getChildren().addAll(folderTitle, autoDetectedRoleBadge, cardSpacer, collapseCardBtn);

        HBox folderInputRow = new HBox(8);
        folderInputRow.setAlignment(Pos.CENTER_LEFT);

        sharedFolderField = new TextField();
        sharedFolderField.setPromptText("Paste Google Drive shared folder link here...");
        sharedFolderField.getStyleClass().add("search-field");
        HBox.setHgrow(sharedFolderField, Priority.ALWAYS);
        String savedFolder = driveService.getSharedFolderUrl();
        if (savedFolder != null && !savedFolder.isBlank()) {
            sharedFolderField.setText(savedFolder);
        }
        sharedFolderField.setOnAction(e -> handleSetSharedFolder(sharedFolderField.getText()));

        Button setFolderBtn = new Button("Link Folder");
        setFolderBtn.setGraphic(AppIcons.createSaveLinkIcon(14));
        setFolderBtn.getStyleClass().addAll("btn", "btn-primary", "btn-sm");
        setFolderBtn.setOnAction(e -> handleSetSharedFolder(sharedFolderField.getText()));

        unlinkFolderBtn = new Button("Unlink");
        unlinkFolderBtn.setGraphic(AppIcons.createTrashIcon(14));
        unlinkFolderBtn.getStyleClass().addAll("btn", "btn-danger", "btn-sm");
        unlinkFolderBtn.setTooltip(new Tooltip("Remove this Google Drive shared folder link"));
        unlinkFolderBtn.setOnAction(e -> handleRemoveDriveLink());
        unlinkFolderBtn.setVisible(driveService.hasSharedFolder());
        unlinkFolderBtn.setManaged(driveService.hasSharedFolder());

        folderInputRow.getChildren().addAll(sharedFolderField, setFolderBtn, unlinkFolderBtn);

        Label folderHint = new Label("Paste any Google Drive shared folder link. Editor access is confirmed by an in-app upload/download probe.");
        folderHint.getStyleClass().add("shared-link-hint");

        sharedFolderCard.getChildren().addAll(cardHeaderRow, folderInputRow, folderHint);

        // Compact Folder Summary Bar (shown when sharedFolderCard is collapsed)
        folderSummaryBar = new HBox(8);
        folderSummaryBar.setAlignment(Pos.CENTER_LEFT);
        folderSummaryBar.getStyleClass().add("folder-summary-bar");
        folderSummaryBar.setVisible(false);
        folderSummaryBar.setManaged(false);

        DriveIcon summaryDriveIcon = new DriveIcon(16);
        summaryFolderIdLabel = new Label("Folder: None");
        summaryFolderIdLabel.getStyleClass().add("folder-summary-title");

        summaryRoleBadge = new Label("Editor");
        summaryRoleBadge.getStyleClass().addAll("status-badge", "status-badge-editor");
        summaryRoleBadge.setCursor(Cursor.HAND);
        summaryRoleBadge.setTooltip(new Tooltip("Auto-detected permission. Click to cycle / override role manually (Editor -> Viewer)."));
        summaryRoleBadge.setOnMouseClicked(e -> cycleFolderPermission());

        Region summarySpacer = new Region();
        HBox.setHgrow(summarySpacer, Priority.ALWAYS);

        Button expandCardBtn = new Button("Change Folder / Settings");
        expandCardBtn.setGraphic(AppIcons.createSettingsIcon(12));
        expandCardBtn.getStyleClass().addAll("btn", "btn-secondary", "btn-sm");
        expandCardBtn.setTooltip(new Tooltip("Show full shared folder configuration box"));
        expandCardBtn.setOnAction(e -> {
            ConfigManager.getInstance().getConfig().setDriveCardCollapsed(false);
            ConfigManager.getInstance().saveConfig();
            updateDriveStatusUI();
        });

        Button summaryOpenBrowserBtn = new Button("Open in Browser");
        summaryOpenBrowserBtn.setGraphic(new DriveIcon(12));
        summaryOpenBrowserBtn.getStyleClass().addAll("btn", "btn-secondary", "btn-sm");
        summaryOpenBrowserBtn.setOnAction(e -> handleOpenDriveFolderInBrowser());

        summaryUnlinkBtn = new Button("Unlink");
        summaryUnlinkBtn.setGraphic(AppIcons.createTrashIcon(12));
        summaryUnlinkBtn.getStyleClass().addAll("btn", "btn-danger", "btn-sm");
        summaryUnlinkBtn.setTooltip(new Tooltip("Remove this Google Drive shared folder link"));
        summaryUnlinkBtn.setOnAction(e -> handleRemoveDriveLink());

        folderSummaryBar.getChildren().addAll(summaryDriveIcon, summaryFolderIdLabel, summaryRoleBadge, summarySpacer, expandCardBtn, summaryOpenBrowserBtn, summaryUnlinkBtn);

        // Search Field
        driveSearchField = new TextField();
        driveSearchField.setPromptText("Search Drive books by title...");
        driveSearchField.getStyleClass().add("search-field");

        // Table
        driveBooksMasterList = FXCollections.observableArrayList();
        driveFilteredList = new FilteredList<>(driveBooksMasterList, p -> true);
        driveSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            driveFilteredList.setPredicate(book -> {
                if (newVal == null || newVal.isBlank()) return true;
                return book.getTitle().toLowerCase().contains(newVal.toLowerCase().trim());
            });
            updateDriveCount();
            if (isDriveIconsView) {
                updateDriveIconsGrid();
            }
        });

        driveTableView = new TableView<>(driveFilteredList);
        driveTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        driveTableView.setPlaceholder(new Label("No Google Drive shared folder configured.\nEnter a shared folder link above and click 'Link Folder'."));
        driveTableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        VBox.setVgrow(driveTableView, Priority.ALWAYS);
        driveTableView.getSelectionModel().getSelectedItems().addListener((ListChangeListener<Book>) change -> {
            if (!syncingDriveSelection) {
                selectedDriveBooks.clear();
                selectedDriveBooks.addAll(driveTableView.getSelectionModel().getSelectedItems());
                selectedDriveBook = driveTableView.getSelectionModel().getSelectedItem();
                updateDriveSelectionUI();
            }
        });

        // Drive PDF cover thumbnail column
        TableColumn<Book, Book> driveCoverCol = new TableColumn<>("Cover");
        driveCoverCol.setPrefWidth(60);
        driveCoverCol.setMinWidth(60);
        driveCoverCol.setMaxWidth(68);
        driveCoverCol.setResizable(false);
        driveCoverCol.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue()));
        driveCoverCol.setCellFactory(col -> new TableCell<>() {
            private final ImageView imageView = new ImageView();
            private final StackPane placeholder = AppIcons.createBookCoverPlaceholder(32, 44);
            private final StackPane container = new StackPane(placeholder, imageView);

            {
                imageView.setFitWidth(32);
                imageView.setFitHeight(44);
                imageView.setPreserveRatio(true);
                imageView.setSmooth(true);
                imageView.getStyleClass().add("book-thumb-image");
                container.setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(Book book, boolean empty) {
                super.updateItem(book, empty);
                if (empty || book == null) {
                    setGraphic(null);
                } else {
                    imageView.setImage(null);
                    placeholder.setVisible(true);
                    setGraphic(container);
                    loadDriveThumbnailAsync(book, imageView, placeholder);
                }
            }
        });

        TableColumn<Book, String> nameCol = new TableColumn<>("Title");
        nameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getTitle()));
        nameCol.setPrefWidth(220);

        TableColumn<Book, String> sizeCol = new TableColumn<>("Size");
        sizeCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFormattedFileSize()));
        sizeCol.setPrefWidth(80);

        TableColumn<Book, String> dateCol = new TableColumn<>("Cloud Date");
        dateCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFormattedLastModified()));
        dateCol.setPrefWidth(120);

        driveTableView.getColumns().addAll(driveCoverCol, nameCol, sizeCol, dateCol);

        // Double-click row to read online without downloading
        driveTableView.setRowFactory(tv -> {
            TableRow<Book> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    handleReadOnlineDriveBook(row.getItem());
                }
            });
            return row;
        });

        // Drive Icons / Bookshelf FlowPane view
        driveIconsFlowPane = new FlowPane();
        driveIconsFlowPane.setHgap(16);
        driveIconsFlowPane.setVgap(16);
        driveIconsFlowPane.setPadding(new Insets(10));
        driveIconsFlowPane.getStyleClass().add("bookshelf-grid");

        driveIconsScrollPane = new ScrollPane(driveIconsFlowPane);
        driveIconsScrollPane.setFitToWidth(true);
        driveIconsScrollPane.setFitToHeight(true);
        driveIconsScrollPane.getStyleClass().add("bookshelf-scroll-pane");
        driveIconsScrollPane.setVisible(false);
        driveIconsScrollPane.setManaged(false);
        VBox.setVgrow(driveIconsScrollPane, Priority.ALWAYS);

        driveViewSwitcherStack = new StackPane(driveTableView, driveIconsScrollPane);
        VBox.setVgrow(driveViewSwitcherStack, Priority.ALWAYS);

        driveDetailsItem.setOnAction(e -> {
            isDriveIconsView = false;
            driveTableView.setVisible(true);
            driveTableView.setManaged(true);
            driveIconsScrollPane.setVisible(false);
            driveIconsScrollPane.setManaged(false);
            driveViewModeMenuBtn.setText("View: Details");
            driveViewModeMenuBtn.setGraphic(AppIcons.createDetailsIcon(14));
            syncDriveSelectionToTable();
        });

        driveIconsItem.setOnAction(e -> {
            isDriveIconsView = true;
            driveTableView.setVisible(false);
            driveTableView.setManaged(false);
            driveIconsScrollPane.setVisible(true);
            driveIconsScrollPane.setManaged(true);
            driveViewModeMenuBtn.setText("View: Icons");
            driveViewModeMenuBtn.setGraphic(AppIcons.createGridIcon(14));
            updateDriveIconsGrid();
        });

        // Bottom Action Buttons
        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_LEFT);

        readOnlineDriveBtn = new Button("Read Online");
        readOnlineDriveBtn.setGraphic(AppIcons.createOpenBookIcon(14, Color.WHITE));
        readOnlineDriveBtn.getStyleClass().addAll("btn", "btn-primary");
        readOnlineDriveBtn.setTooltip(new Tooltip("Stream and read this book online without downloading to local Books"));
        readOnlineDriveBtn.setOnAction(e -> handleReadOnlineSelectedDriveBook());

        downloadDriveBtn = new Button("Download to Local Books");
        downloadDriveBtn.setGraphic(AppIcons.createDownloadIcon(14, Color.web("#4a5568")));
        downloadDriveBtn.setText("");
        downloadDriveBtn.getStyleClass().addAll("btn", "btn-secondary", "icon-btn");
        downloadDriveBtn.setTooltip(new Tooltip("Save a permanent copy of this book to your local Books library"));
        downloadDriveBtn.setOnAction(e -> handleDownloadSelectedDriveBook());

        refreshDriveBtn = new Button("Refresh Folder");
        refreshDriveBtn.setGraphic(AppIcons.createRefreshIcon(14));
        refreshDriveBtn.setText("");
        refreshDriveBtn.getStyleClass().addAll("btn", "btn-secondary", "icon-btn");
        refreshDriveBtn.setOnAction(e -> loadDriveBooks());

        renameDriveBtn = new Button("Rename");
        renameDriveBtn.setGraphic(AppIcons.createPenIcon(14, Color.web("#4a5568")));
        renameDriveBtn.setText("");
        renameDriveBtn.getStyleClass().addAll("btn", "btn-secondary", "icon-btn");
        renameDriveBtn.setOnAction(e -> handleRenameSelectedDriveBook());

        deleteDriveBtn = new Button("Delete");
        deleteDriveBtn.setGraphic(AppIcons.createTrashIcon(14));
        deleteDriveBtn.setText("");
        deleteDriveBtn.getStyleClass().addAll("btn", "btn-danger", "icon-btn");
        deleteDriveBtn.setOnAction(e -> handleDeleteSelectedDriveBooks());

        actions.getChildren().addAll(readOnlineDriveBtn, downloadDriveBtn, renameDriveBtn, deleteDriveBtn, refreshDriveBtn);

        panel.getChildren().addAll(header, sharedFolderCard, folderSummaryBar, driveSearchField, driveViewSwitcherStack, actions);
        return panel;
    }

    private HBox createStatusBar() {
        HBox statusBar = new HBox(12);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(6, 16, 6, 16));
        statusBar.getStyleClass().add("status-bar");

        statusMessageLabel = new Label("Ready");
        statusMessageLabel.getStyleClass().add("status-message");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        taskProgressBar = new ProgressBar(0);
        taskProgressBar.setPrefWidth(150);
        taskProgressBar.setVisible(false);

        Label pathInfo = new Label("Folder: " + bookService.getBooksDirectory().toString());
        pathInfo.setStyle("-fx-text-fill: #777; -fx-font-size: 11px;");

        statusBar.getChildren().addAll(statusMessageLabel, spacer, taskProgressBar, pathInfo);
        return statusBar;
    }

    public void loadLocalBooks() {
        loadLocalBooks(null);
    }

    public void loadLocalBooks(Runnable onComplete) {
        Thread thread = new Thread(() -> {
            List<Book> books = bookService.listLocalBooks();
            Platform.runLater(() -> {
                localBooksMasterList.setAll(books);
                selectedLocalBooks.retainAll(books);
                if (selectedBook != null && !selectedLocalBooks.contains(selectedBook)) {
                    selectedBook = selectedLocalBooks.stream().findFirst().orElse(null);
                }
                updateLocalCount();
                setStatus("Local library refreshed (" + books.size() + " books found)");
                if (isLocalIconsView) {
                    updateLocalIconsGrid();
                }
                syncLocalSelectionToTable();
                updateLocalSelectionUI();
                if (onComplete != null) {
                    onComplete.run();
                }
            });
        }, "local-books-loader");
        thread.setDaemon(true);
        thread.start();
    }

    private void updateLocalCount() {
        int count = localFilteredList.size();
        localCountLabel.setText(count + (count == 1 ? " book" : " books"));
        if (homeLocalCountLabel != null) {
            homeLocalCountLabel.setText(count + (count == 1 ? " book" : " books"));
        }
    }

    private void updateDriveCount() {
        if (driveCountLabel != null) {
            int count = driveFilteredList != null ? driveFilteredList.size() : 0;
            driveCountLabel.setText(count + (count == 1 ? " book" : " books"));
            if (homeDriveCountLabel != null) {
                homeDriveCountLabel.setText(count + (count == 1 ? " book" : " books"));
            }
        }
    }

    public void loadDriveBooks() {
        String folderUrl = driveService.getSharedFolderUrl();
        if (folderUrl == null || folderUrl.isBlank()) {
            driveBooksMasterList.clear();
            selectedDriveBooks.clear();
            selectedDriveBook = null;
            updateDriveCount();
            if (isDriveIconsView) {
                updateDriveIconsGrid();
            }
            syncDriveSelectionToTable();
            updateDriveSelectionUI();
            driveTableView.setPlaceholder(new Label("No Google Drive shared folder configured.\nEnter a shared folder link above and click 'Link Folder'."));
            updateDriveStatusUI();
            return;
        }

        setStatus("Fetching books from Google Drive shared folder...");
        taskProgressBar.setVisible(true);
        taskProgressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);

        Thread fetchThread = new Thread(() -> {
            try {
                driveService.autoDetectFolderPermission(folderUrl);

                List<Book> cloudBooks = driveService.listSharedFolderBooks(folderUrl);
                Platform.runLater(() -> {
                    driveBooksMasterList.setAll(cloudBooks);
                    selectedDriveBooks.retainAll(cloudBooks);
                    if (selectedDriveBook != null && !selectedDriveBooks.contains(selectedDriveBook)) {
                        selectedDriveBook = selectedDriveBooks.stream().findFirst().orElse(null);
                    }
                    updateDriveCount();
                    if (isDriveIconsView) {
                        updateDriveIconsGrid();
                    }
                    syncDriveSelectionToTable();
                    updateDriveSelectionUI();
                    taskProgressBar.setVisible(false);
                    setStatus("Drive library loaded (" + cloudBooks.size() + " books, "
                            + (driveService.isEditor() ? "Editor / Read & Write" : "Viewer / Download Only") + ")");
                    if (cloudBooks.isEmpty()) {
                        driveTableView.setPlaceholder(new Label("No PDF books found in your Google Drive shared folder.\nSign in and upload selected Local Library PDFs from inside the app."));
                    }
                    updateDriveStatusUI();
                });

                // Resolve exact file sizes in background for any books missing size
                driveService.resolveCloudBookSizesAsync(cloudBooks, updatedBook -> {
                    Platform.runLater(() -> {
                        driveTableView.refresh();
                        if (isDriveIconsView) {
                            updateDriveIconsGrid();
                        }
                    });
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    taskProgressBar.setVisible(false);
                    setStatus("Failed to load Drive books: " + e.getMessage());
                    driveTableView.setPlaceholder(new Label("Failed to load books: " + e.getMessage()));
                    updateDriveStatusUI();
                });
            }
        }, "drive-list-thread");
        fetchThread.setDaemon(true);
        fetchThread.start();
    }

    private void handleImportLocalPdf() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import PDF to Local Library");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Documents (*.pdf)", "*.pdf"));

        File file = chooser.showOpenDialog(primaryStage);
        if (file != null) {
            try {
                Book imported = bookService.importBook(file);
                loadLocalBooks();
                setStatus("Imported '" + imported.getTitle() + "' into local library.");
                showInfo("Import Successful", "'" + imported.getTitle() + "' has been copied to your Books library.");
            } catch (Exception e) {
                showError("Import Failed", e.getMessage());
            }
        }
    }

    private void handleOpenBooksFolderInExplorer() {
        try {
            Desktop.getDesktop().open(bookService.getBooksDirectory().toFile());
        } catch (Exception e) {
            showError("Cannot Open Folder", "Failed to open file manager: " + e.getMessage());
        }
    }

    private void handleDeleteSelectedLocalBook() {
        List<Book> selectedBooks = getSelectedLocalBooks();
        if (selectedBooks.isEmpty()) {
            showInfo("No Selection", "Please select a book to delete.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Deletion");
        confirm.setHeaderText(selectedBooks.size() == 1 ? "Delete Local Book" : "Delete Local Books");
        confirm.setContentText(selectedBooks.size() == 1
                ? "Are you sure you want to delete '" + selectedBooks.get(0).getTitle() + "' from local storage?\nThis cannot be undone."
                : "Are you sure you want to delete " + selectedBooks.size() + " selected local books?\nThis cannot be undone.");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                int deleted = 0;
                for (Book book : selectedBooks) {
                    if (bookService.deleteBook(book)) {
                        deleted++;
                    }
                }
                selectedLocalBooks.removeAll(selectedBooks);
                selectedBook = null;
                loadLocalBooks();
                setStatus("Deleted " + deleted + " local " + (deleted == 1 ? "book" : "books") + ".");
                if (deleted != selectedBooks.size()) {
                    showError("Delete Incomplete", "Deleted " + deleted + " of " + selectedBooks.size() + " selected books.");
                }
            }
        });
    }

    private void handleRenameSelectedLocalBook() {
        List<Book> selectedBooks = getSelectedLocalBooks();
        if (selectedBooks.size() != 1) {
            showInfo("Rename One Book", "Please select exactly one local book to rename.");
            return;
        }

        Book selected = selectedBooks.get(0);
        TextInputDialog dialog = new TextInputDialog(selected.getTitle());
        dialog.setTitle("Rename Local Book");
        dialog.setHeaderText("Rename Local PDF");
        dialog.setContentText("New file name:");

        Optional<String> result = dialog.showAndWait();
        result.map(this::normalizePdfFileName).ifPresent(newName -> {
            if (newName.isBlank()) {
                showInfo("Invalid Name", "Please enter a file name.");
                return;
            }
            try {
                Book renamed = bookService.renameBook(selected, newName);
                loadLocalBooks(() -> selectBook(renamed));
                setStatus("Renamed local book to '" + renamed.getTitle() + "'.");
            } catch (Exception e) {
                showError("Rename Failed", e.getMessage());
            }
        });
    }

    private void handleSetSharedFolder(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            showInfo("Shared Folder Link Required", "Please enter or paste a Google Drive shared folder link.");
            return;
        }

        String url = rawUrl.trim();
        String folderId = DriveService.extractIdFromShareLink(url);
        if (folderId.isEmpty()) {
            showError("Invalid Folder Link", "Could not extract a valid Google Drive folder ID from:\n" + url);
            return;
        }

        driveService.setSharedFolderUrl(url);

        if (autoDetectedRoleBadge != null) {
            autoDetectedRoleBadge.setText("Auto-detecting permissions...");
            autoDetectedRoleBadge.getStyleClass().setAll("status-badge", "status-badge-disconnected");
            autoDetectedRoleBadge.setVisible(true);
            autoDetectedRoleBadge.setManaged(true);
        }
        if (summaryRoleBadge != null) {
            summaryRoleBadge.setText("Auto-detecting...");
            summaryRoleBadge.getStyleClass().setAll("status-badge", "status-badge-disconnected");
        }

        // Keep card expanded so user sees the auto-detected role clearly
        ConfigManager.getInstance().getConfig().setDriveCardCollapsed(false);
        ConfigManager.getInstance().saveConfig();

        updateDriveStatusUI();
        setStatus("Shared folder linked — detecting permissions...");

        loadDriveBooks();
    }

    private void handleRemoveDriveLink() {
        if (!driveService.hasSharedFolder()) {
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Remove Google Drive Link");
        confirm.setHeaderText("Unlink Google Drive Folder");
        confirm.setContentText("Are you sure you want to unlink and remove the Google Drive shared folder?\nYour local books will remain untouched.");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                driveService.unlinkSharedFolder();
                driveBooksMasterList.clear();
                selectedDriveBooks.clear();
                selectedDriveBook = null;
                if (sharedFolderField != null) {
                    sharedFolderField.setText("");
                }
                updateDriveCount();
                if (isDriveIconsView) {
                    updateDriveIconsGrid();
                }
                syncDriveSelectionToTable();
                updateDriveSelectionUI();
                updateDriveStatusUI();
                setStatus("Google Drive shared folder unlinked.");
            }
        });
    }

    private void handleOpenDriveFolderInBrowser() {
        String folderUrl = driveService.getSharedFolderUrl();
        if (folderUrl == null || folderUrl.isBlank()) {
            showInfo("No Folder Configured", "Please paste and link your Google Drive shared folder first.");
            return;
        }
        handleOpenSharedLinkInBrowser(folderUrl);
    }

    private void handleUploadSelectedBook() {
        if (!driveService.isAuthenticated()) {
            showSignInRequired("Upload requires Google Drive sign-in.");
            return;
        }
        if (!driveService.canUpload()) {
            showInfo("Viewer Permission", "This Google Drive folder is currently Viewer / download-only.\n"
                    + "The app enables uploads only after an automatic upload/download probe confirms Editor access.");
            return;
        }

        List<Book> selectedBooks = getSelectedLocalBooks();
        if (selectedBooks.isEmpty()) {
            showInfo("No Book Selected", "Please select one or more local books to upload to Google Drive.");
            return;
        }

        String folderUrl = driveService.getSharedFolderUrl();
        if (folderUrl == null || folderUrl.isBlank()) {
            showInfo("No Shared Folder Configured", "Please link your Google Drive shared folder in the Drive panel first.");
            return;
        }

        taskProgressBar.setVisible(true);
        taskProgressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        setStatus("Uploading " + selectedBooks.size() + " local " + (selectedBooks.size() == 1 ? "book" : "books") + " to Google Drive...");

        Thread uploadThread = new Thread(() -> {
            List<Book> uploadedBooks = new ArrayList<>();
            List<String> failures = new ArrayList<>();
            for (int i = 0; i < selectedBooks.size(); i++) {
                Book localBook = selectedBooks.get(i);
                int index = i + 1;
                Book uploaded = driveService.uploadBookToSharedFolder(localBook, folderUrl, new DriveService.TransferListener() {
                    @Override
                    public void onProgress(String message, double fraction) {
                        Platform.runLater(() -> {
                            setStatus("Uploading " + index + " of " + selectedBooks.size() + ": " + message);
                            taskProgressBar.setProgress(selectedBooks.size() == 1
                                    ? fraction
                                    : Math.min(0.98, ((index - 1) + Math.max(0, fraction)) / selectedBooks.size()));
                        });
                    }

                    @Override
                    public void onComplete(String successMessage) {
                        Platform.runLater(() -> setStatus(successMessage));
                    }

                    @Override
                    public void onError(String errorMessage, Throwable throwable) {
                        failures.add(localBook.getTitle() + ": " + errorMessage);
                    }
                });
                if (uploaded != null) {
                    uploadedBooks.add(uploaded);
                }
            }

            Platform.runLater(() -> {
                taskProgressBar.setVisible(false);
                if (!uploadedBooks.isEmpty()) {
                    for (Book uploaded : uploadedBooks) {
                        if (!driveBooksMasterList.contains(uploaded)) {
                            driveBooksMasterList.add(uploaded);
                        }
                    }
                    driveBooksMasterList.sort((a, b) -> a.getTitle().compareToIgnoreCase(b.getTitle()));
                    updateDriveCount();
                    if (isDriveIconsView) {
                        updateDriveIconsGrid();
                    }
                }
                if (failures.isEmpty()) {
                    setStatus("Uploaded " + uploadedBooks.size() + " " + (uploadedBooks.size() == 1 ? "book" : "books") + " to Google Drive.");
                } else {
                    setStatus("Uploaded " + uploadedBooks.size() + " of " + selectedBooks.size() + " selected books.");
                    showError("Upload Incomplete", String.join("\n", failures));
                }
            });
        }, "drive-upload-thread");
        uploadThread.setDaemon(true);
        uploadThread.start();
    }

    private void handleDownloadSelectedDriveBook() {
        List<Book> selectedBooks = getSelectedDriveBooks();
        if (selectedBooks.isEmpty()) {
            showInfo("No Book Selected", "Please select one or more books from the Google Drive panel to download.");
            return;
        }

        taskProgressBar.setVisible(true);
        taskProgressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        setStatus("Downloading " + selectedBooks.size() + " Drive " + (selectedBooks.size() == 1 ? "book" : "books") + " to local Books...");

        Thread downloadThread = new Thread(() -> {
            List<Book> downloadedBooks = new ArrayList<>();
            List<String> failures = new ArrayList<>();
            for (int i = 0; i < selectedBooks.size(); i++) {
                Book selected = selectedBooks.get(i);
                int index = i + 1;
                Book downloadedBook = driveService.downloadPublicSharedFile(selected.getId(), bookService.getBooksDirectory(), selected.getTitle(), new DriveService.TransferListener() {
                    @Override
                    public void onProgress(String message, double fraction) {
                        Platform.runLater(() -> {
                            setStatus("Downloading " + index + " of " + selectedBooks.size() + ": " + message);
                            taskProgressBar.setProgress(selectedBooks.size() == 1
                                    ? fraction
                                    : Math.min(0.98, ((index - 1) + Math.max(0, fraction)) / selectedBooks.size()));
                        });
                    }

                    @Override
                    public void onComplete(String successMessage) {
                    }

                    @Override
                    public void onError(String errorMessage, Throwable throwable) {
                        failures.add(selected.getTitle() + ": " + errorMessage);
                    }
                });

                if (downloadedBook != null) {
                    downloadedBooks.add(downloadedBook);
                }
            }

            Platform.runLater(() -> {
                taskProgressBar.setVisible(false);
                for (Book downloadedBook : downloadedBooks) {
                    boolean updated = false;
                    for (int i = 0; i < localBooksMasterList.size(); i++) {
                        if (localBooksMasterList.get(i).getPath().equalsIgnoreCase(downloadedBook.getPath())) {
                            localBooksMasterList.set(i, downloadedBook);
                            updated = true;
                            break;
                        }
                    }
                    if (!updated) {
                        localBooksMasterList.add(0, downloadedBook);
                    }
                }
                updateLocalCount();
                if (!downloadedBooks.isEmpty()) {
                    selectedLocalBooks.clear();
                    selectedLocalBooks.addAll(downloadedBooks);
                    selectedBook = downloadedBooks.get(0);
                    if (isLocalIconsView) {
                        updateLocalIconsGrid();
                    }
                    syncLocalSelectionToTable();
                    updateLocalSelectionUI();
                    localTableView.scrollTo(downloadedBooks.get(0));
                }
                if (failures.isEmpty()) {
                    setStatus("Downloaded " + downloadedBooks.size() + " " + (downloadedBooks.size() == 1 ? "book" : "books") + " to local library.");
                } else {
                    setStatus("Downloaded " + downloadedBooks.size() + " of " + selectedBooks.size() + " selected books.");
                    showError("Download Incomplete", String.join("\n", failures));
                }
            });
        }, "download-thread");
        downloadThread.setDaemon(true);
        downloadThread.start();
    }

    private void handleRenameSelectedDriveBook() {
        if (!driveService.isAuthenticated()) {
            showSignInRequired("Rename requires Google Drive sign-in.");
            return;
        }
        if (!driveService.canUpload()) {
            showInfo("Viewer Permission", "Rename requires Editor access to the linked Google Drive folder.");
            return;
        }

        List<Book> selectedBooks = getSelectedDriveBooks();
        if (selectedBooks.size() != 1) {
            showInfo("Rename One Book", "Please select exactly one Drive book to rename.");
            return;
        }

        Book selected = selectedBooks.get(0);
        TextInputDialog dialog = new TextInputDialog(selected.getTitle());
        dialog.setTitle("Rename Drive Book");
        dialog.setHeaderText("Rename Google Drive PDF");
        dialog.setContentText("New file name:");

        Optional<String> result = dialog.showAndWait();
        result.map(this::normalizePdfFileName).ifPresent(newName -> {
            if (newName.isBlank()) {
                showInfo("Invalid Name", "Please enter a file name.");
                return;
            }
            String oldName = selected.getTitle();
            selected.setTitle(newName);
            driveTableView.refresh();
            if (isDriveIconsView) {
                updateDriveIconsGrid();
            }
            setStatus("Renaming '" + oldName + "'...");

            Thread renameThread = new Thread(() -> {
                try {
                    Book renamed = driveService.renameCloudBook(selected, newName);
                    Platform.runLater(() -> {
                        selected.setTitle(renamed.getTitle());
                        selected.setLastModifiedEpochMs(renamed.getLastModifiedEpochMs());
                        driveTableView.refresh();
                        if (isDriveIconsView) {
                            updateDriveIconsGrid();
                        }
                        setStatus("Renamed Drive book to '" + renamed.getTitle() + "'.");
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        selected.setTitle(oldName);
                        driveTableView.refresh();
                        if (isDriveIconsView) {
                            updateDriveIconsGrid();
                        }
                        setStatus("Drive rename failed: " + e.getMessage());
                        showError("Rename Failed", e.getMessage());
                    });
                }
            }, "drive-rename-thread");
            renameThread.setDaemon(true);
            renameThread.start();
        });
    }

    private void handleDeleteSelectedDriveBooks() {
        if (!driveService.isAuthenticated()) {
            showSignInRequired("Delete requires Google Drive sign-in.");
            return;
        }
        if (!driveService.canUpload()) {
            showInfo("Viewer Permission", "Delete requires Editor access to the linked Google Drive folder.");
            return;
        }

        List<Book> selectedBooks = getSelectedDriveBooks();
        if (selectedBooks.isEmpty()) {
            showInfo("No Selection", "Please select one or more Drive books to delete.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Drive Book");
        confirm.setHeaderText(selectedBooks.size() == 1 ? "Delete Drive Book" : "Delete Drive Books");
        confirm.setContentText(selectedBooks.size() == 1
                ? "Delete '" + selectedBooks.get(0).getTitle() + "' from Google Drive?\nThis cannot be undone."
                : "Delete " + selectedBooks.size() + " selected books from Google Drive?\nThis cannot be undone.");

        confirm.showAndWait().ifPresent(response -> {
            if (response != ButtonType.OK) {
                return;
            }

            driveBooksMasterList.removeAll(selectedBooks);
            selectedDriveBooks.removeAll(selectedBooks);
            selectedDriveBook = selectedDriveBooks.stream().findFirst().orElse(null);
            updateDriveCount();
            if (isDriveIconsView) {
                updateDriveIconsGrid();
            }
            syncDriveSelectionToTable();
            updateDriveSelectionUI();
            taskProgressBar.setVisible(true);
            taskProgressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
            setStatus("Deleting " + selectedBooks.size() + " Drive " + (selectedBooks.size() == 1 ? "book" : "books") + "...");

            Thread deleteThread = new Thread(() -> {
                List<Book> failedDeletes = new ArrayList<>();
                List<String> failures = new ArrayList<>();
                for (int i = 0; i < selectedBooks.size(); i++) {
                    Book book = selectedBooks.get(i);
                    int index = i + 1;
                    Platform.runLater(() -> {
                        setStatus("Deleting " + index + " of " + selectedBooks.size() + ": " + book.getTitle());
                        taskProgressBar.setProgress((double) index / selectedBooks.size());
                    });
                    try {
                        driveService.deleteCloudBook(book);
                    } catch (Exception e) {
                        failedDeletes.add(book);
                        failures.add(book.getTitle() + ": " + e.getMessage());
                    }
                }

                Platform.runLater(() -> {
                    taskProgressBar.setVisible(false);
                    if (!failedDeletes.isEmpty()) {
                        driveBooksMasterList.addAll(failedDeletes);
                        driveBooksMasterList.sort((a, b) -> a.getTitle().compareToIgnoreCase(b.getTitle()));
                        updateDriveCount();
                        if (isDriveIconsView) {
                            updateDriveIconsGrid();
                        }
                        showError("Delete Incomplete", String.join("\n", failures));
                    }
                    int deleted = selectedBooks.size() - failedDeletes.size();
                    setStatus("Deleted " + deleted + " Drive " + (deleted == 1 ? "book" : "books") + ".");
                });
            }, "drive-delete-thread");
            deleteThread.setDaemon(true);
            deleteThread.start();
        });
    }

    private void handleReadOnlineSelectedDriveBook() {
        Book selected = getSelectedDriveBook();
        if (selected == null) {
            showInfo("No Book Selected", "Please select a book from the Google Drive panel to read online.");
            return;
        }
        handleReadOnlineDriveBook(selected);
    }

    private void handleReadOnlineDriveBook(Book selected) {
        if (selected == null) return;

        // If book already has a valid local or cached path, open immediately!
        if (selected.getPath() != null && new File(selected.getPath()).exists()) {
            openBookInReader(selected);
            return;
        }

        taskProgressBar.setVisible(true);
        taskProgressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        setStatus("Streaming '" + selected.getTitle() + "' for online reading (no download needed)...");

        Thread streamThread = new Thread(() -> {
            try {
                File cachedFile = driveService.streamCloudFileForOnlineReading(selected.getId(), selected.getTitle(), new DriveService.TransferListener() {
                    @Override
                    public void onProgress(String message, double fraction) {
                        Platform.runLater(() -> {
                            setStatus(message);
                            taskProgressBar.setProgress(fraction);
                        });
                    }

                    @Override
                    public void onComplete(String successMessage) {
                        Platform.runLater(() -> {
                            taskProgressBar.setVisible(false);
                            setStatus(successMessage);
                        });
                    }

                    @Override
                    public void onError(String errorMessage, Throwable throwable) {
                        Platform.runLater(() -> {
                            taskProgressBar.setVisible(false);
                            setStatus("Streaming error: " + errorMessage);
                            showError("Online Reading Failed", errorMessage);
                        });
                    }
                });

                if (cachedFile != null && cachedFile.exists()) {
                    selected.setPath(cachedFile.getAbsolutePath());
                    if (selected.getFileSizeBytes() <= 0) {
                        selected.setFileSizeBytes(cachedFile.length());
                    }
                    Platform.runLater(() -> {
                        taskProgressBar.setVisible(false);
                        driveTableView.refresh();
                        if (isDriveIconsView) {
                            updateDriveIconsGrid();
                        }
                        openBookInReader(selected);
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    taskProgressBar.setVisible(false);
                    setStatus("Online streaming failed: " + e.getMessage());
                    showError("Online Reading Failed", "Could not stream file for online reading: " + e.getMessage());
                });
            }
        }, "online-stream-thread");
        streamThread.setDaemon(true);
        streamThread.start();
    }

    public void openBookInReader(Book book) {
        if (book == null) return;

        // Check if physical file exists on disk
        if (book.getPath() == null || !new File(book.getPath()).exists()) {
            if (book.isCloud()) {
                handleReadOnlineDriveBook(book);
                return;
            } else {
                showError("File Not Found", "The PDF book file could not be found:\n" + book.getPath());
                return;
            }
        }

        libraryViewPane.setVisible(false);
        pdfReaderView.setVisible(true);
        pdfReaderView.openBook(book);
        setStatus("Reading: " + book.getTitle() + (book.isCloud() ? " (Online)" : ""));
    }

    public void showLibraryView() {
        pdfReaderView.setVisible(false);
        libraryViewPane.setVisible(true);
        setStatus("Library view");
    }

    public void updateDriveStatusUI() {
        boolean hasFolder = driveService.hasSharedFolder();
        boolean isEditor = driveService.isEditor();
        boolean isCollapsed = ConfigManager.getInstance().getConfig().isDriveCardCollapsed();

        if (hasFolder) {
            String folderUrl = driveService.getSharedFolderUrl();
            String folderId = DriveService.extractIdFromShareLink(folderUrl);
            String shortId = folderId.length() > 10 ? folderId.substring(0, 10) + "..." : folderId;

            if (summaryFolderIdLabel != null) {
                summaryFolderIdLabel.setText("Linked Folder: " + shortId);
            }

            if (isEditor) {
                driveStatusBadge.setText(driveService.isAuthenticated()
                        ? "Linked: Editor (Signed In)"
                        : "Linked: Editor (Sign In Required)");
                driveStatusBadge.getStyleClass().setAll("status-badge", "status-badge-editor");

                if (summaryRoleBadge != null) {
                    summaryRoleBadge.setText(driveService.isAuthenticated()
                            ? "Editor (Signed In)"
                            : "Editor (Sign In Required)");
                    summaryRoleBadge.getStyleClass().setAll("status-badge", "status-badge-editor");
                }

                if (autoDetectedRoleBadge != null) {
                    autoDetectedRoleBadge.setText(driveService.isAuthenticated()
                            ? "Auto-detected: Editor (Read & Write)"
                            : "Auto-detected: Editor (Sign In Required)");
                    autoDetectedRoleBadge.getStyleClass().setAll("status-badge", "status-badge-editor");
                    autoDetectedRoleBadge.setVisible(true);
                    autoDetectedRoleBadge.setManaged(true);
                }
            } else {
                driveStatusBadge.setText("Linked: Viewer (Download Only)");
                driveStatusBadge.getStyleClass().setAll("status-badge", "status-badge-viewer");

                if (summaryRoleBadge != null) {
                    summaryRoleBadge.setText("Viewer (Download Only)");
                    summaryRoleBadge.getStyleClass().setAll("status-badge", "status-badge-viewer");
                }

                if (autoDetectedRoleBadge != null) {
                    autoDetectedRoleBadge.setText("Auto-detected: Viewer (Download Only)");
                    autoDetectedRoleBadge.getStyleClass().setAll("status-badge", "status-badge-viewer");
                    autoDetectedRoleBadge.setVisible(true);
                    autoDetectedRoleBadge.setManaged(true);
                }
            }

            // Toggle sharedFolderCard vs folderSummaryBar
            if (sharedFolderCard != null && folderSummaryBar != null) {
                if (isCollapsed) {
                    sharedFolderCard.setVisible(false);
                    sharedFolderCard.setManaged(false);
                    folderSummaryBar.setVisible(true);
                    folderSummaryBar.setManaged(true);
                } else {
                    sharedFolderCard.setVisible(true);
                    sharedFolderCard.setManaged(true);
                    folderSummaryBar.setVisible(false);
                    folderSummaryBar.setManaged(false);
                }
            }

            if (unlinkFolderBtn != null) {
                unlinkFolderBtn.setVisible(true);
                unlinkFolderBtn.setManaged(true);
            }
            if (summaryUnlinkBtn != null) {
                summaryUnlinkBtn.setVisible(true);
                summaryUnlinkBtn.setManaged(true);
            }
        } else {
            driveStatusBadge.setText("No Folder Linked");
            driveStatusBadge.getStyleClass().setAll("status-badge", "status-badge-disconnected");

            if (autoDetectedRoleBadge != null) {
                autoDetectedRoleBadge.setVisible(false);
                autoDetectedRoleBadge.setManaged(false);
            }

            if (sharedFolderCard != null && folderSummaryBar != null) {
                sharedFolderCard.setVisible(true);
                sharedFolderCard.setManaged(true);
                folderSummaryBar.setVisible(false);
                folderSummaryBar.setManaged(false);
            }

            if (unlinkFolderBtn != null) {
                unlinkFolderBtn.setVisible(false);
                unlinkFolderBtn.setManaged(false);
            }
            if (summaryUnlinkBtn != null) {
                summaryUnlinkBtn.setVisible(false);
                summaryUnlinkBtn.setManaged(false);
            }

        }
        updateActionButtonState();
    }

    private void updateActionButtonState() {
        int localCount = selectedLocalBooks.size();
        int driveCount = selectedDriveBooks.size();
        boolean hasDriveFolder = driveService.hasSharedFolder();
        boolean isSignedIn = driveService.isAuthenticated();
        boolean hasEditorFolder = hasDriveFolder && driveService.canUpload();
        boolean canWriteDrive = hasEditorFolder && isSignedIn;

        if (openLocalBtn != null) {
            openLocalBtn.setDisable(localCount == 0);
            openLocalBtn.setTooltip(new Tooltip(localCount > 1 ? "Open the primary selected local book" : "Open selected local book"));
        }
        if (uploadLocalBtn != null) {
            uploadLocalBtn.setDisable(localCount == 0 || !hasDriveFolder || (isSignedIn && !hasEditorFolder));
            if (!hasDriveFolder) {
                uploadLocalBtn.setTooltip(new Tooltip("Link a Google Drive folder first"));
            } else if (!isSignedIn) {
                uploadLocalBtn.setTooltip(new Tooltip("Sign in with Google Drive to upload"));
            } else if (!driveService.canUpload()) {
                uploadLocalBtn.setTooltip(new Tooltip("Uploads are disabled until Editor access is confirmed"));
            } else if (localCount == 0) {
                uploadLocalBtn.setTooltip(new Tooltip("Select one or more local books to upload"));
            } else {
                uploadLocalBtn.setTooltip(new Tooltip("Upload selected local books to Google Drive"));
            }
        }
        if (renameLocalBtn != null) {
            renameLocalBtn.setDisable(localCount != 1);
            renameLocalBtn.setTooltip(new Tooltip("Rename one selected local book"));
        }
        if (deleteLocalBtn != null) {
            deleteLocalBtn.setDisable(localCount == 0);
            deleteLocalBtn.setTooltip(new Tooltip("Delete selected local books"));
        }

        if (readOnlineDriveBtn != null) {
            readOnlineDriveBtn.setDisable(driveCount == 0);
            readOnlineDriveBtn.setTooltip(new Tooltip(driveCount > 1 ? "Read the primary selected Drive book" : "Read selected Drive book online"));
        }
        if (downloadDriveBtn != null) {
            downloadDriveBtn.setDisable(driveCount == 0);
            downloadDriveBtn.setTooltip(new Tooltip("Download selected Drive books to the local library"));
        }
        if (renameDriveBtn != null) {
            renameDriveBtn.setDisable(driveCount != 1 || (isSignedIn && !hasEditorFolder));
            renameDriveBtn.setTooltip(new Tooltip(canWriteDrive ? "Rename one selected Drive book" : "Rename requires Google sign-in and Editor access"));
        }
        if (deleteDriveBtn != null) {
            deleteDriveBtn.setDisable(driveCount == 0 || (isSignedIn && !hasEditorFolder));
            deleteDriveBtn.setTooltip(new Tooltip(canWriteDrive ? "Delete selected Drive books" : "Delete requires Google sign-in and Editor access"));
        }
        updateGoogleAuthUI();
    }

    private void cycleFolderPermission() {
        if (!driveService.hasSharedFolder()) return;
        if (driveService.isEditor()) {
            driveService.setFolderPermission("VIEWER");
            setStatus("Permission overridden to Viewer (Download Only)");
        } else {
            driveService.setFolderPermission("EDITOR");
            setStatus("Permission overridden to Editor (Read & Write)");
        }
        updateDriveStatusUI();
    }

    private String normalizePdfFileName(String rawName) {
        if (rawName == null) return "";
        String clean = rawName.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        if (clean.isBlank()) return "";
        if (!clean.toLowerCase().endsWith(".pdf")) {
            clean += ".pdf";
        }
        return clean;
    }

    private void setStatus(String message) {
        statusMessageLabel.setText(message);
    }

    private void showInfo(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    private void showError(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    private void selectBook(Book book) {
        selectedLocalBooks.clear();
        if (book != null) {
            selectedLocalBooks.add(book);
        }
        selectedBook = book;
        syncLocalSelectionToTable();
        updateLocalSelectionUI();
    }

    private void handleLocalCardSelection(Book book, boolean extendSelection) {
        if (book == null) return;
        if (extendSelection) {
            if (selectedLocalBooks.contains(book)) {
                selectedLocalBooks.remove(book);
            } else {
                selectedLocalBooks.add(book);
                selectedBook = book;
            }
        } else {
            selectedLocalBooks.clear();
            selectedLocalBooks.add(book);
            selectedBook = book;
        }
        if (!selectedLocalBooks.contains(selectedBook)) {
            selectedBook = selectedLocalBooks.stream().findFirst().orElse(null);
        }
        syncLocalSelectionToTable();
        updateLocalSelectionUI();
    }

    private void syncLocalSelectionToTable() {
        if (localTableView == null) return;
        syncingLocalSelection = true;
        localTableView.getSelectionModel().clearSelection();
        for (Book book : selectedLocalBooks) {
            localTableView.getSelectionModel().select(book);
        }
        if (selectedBook != null && selectedLocalBooks.contains(selectedBook)) {
            localTableView.getSelectionModel().select(selectedBook);
        }
        syncingLocalSelection = false;
    }

    private void updateLocalSelectionUI() {
        int selectedCount = selectedLocalBooks.size();
        if (selectedCount == 0) {
            selectedBook = null;
            previewTitle.setText("Select a book to preview & read");
            previewDetails.setText("Double-click a book or click 'Open Book'");
            previewCover.setImage(null);
        } else if (selectedCount == 1) {
            selectedBook = selectedLocalBooks.stream().findFirst().orElse(null);
            previewTitle.setText(selectedBook.getTitle());
            previewDetails.setText(selectedBook.getFormattedFileSize() + "  |  " + selectedBook.getFormattedLastModified());
            previewCover.setImage(null);
            loadThumbnailAsync(selectedBook, previewCover, null);
        } else {
            if (selectedBook == null || !selectedLocalBooks.contains(selectedBook)) {
                selectedBook = selectedLocalBooks.stream().findFirst().orElse(null);
            }
            previewTitle.setText(selectedCount + " local books selected");
            previewDetails.setText("Batch upload and delete are available");
            previewCover.setImage(null);
        }

        for (javafx.scene.Node node : localIconsFlowPane.getChildren()) {
            if (node instanceof VBox cardBox) {
                updateCardSelectionStyle(cardBox, selectedLocalBooks.contains(cardBox.getUserData()));
            }
        }
        updateActionButtonState();
    }

    private Book getSelectedBook() {
        if (selectedBook != null && selectedLocalBooks.contains(selectedBook)) {
            return selectedBook;
        }
        if (!selectedLocalBooks.isEmpty()) {
            return selectedLocalBooks.stream().findFirst().orElse(null);
        }
        Book tableSelected = localTableView.getSelectionModel().getSelectedItem();
        return tableSelected != null ? tableSelected : selectedBook;
    }

    private List<Book> getSelectedLocalBooks() {
        if (!selectedLocalBooks.isEmpty()) {
            return new ArrayList<>(selectedLocalBooks);
        }
        Book selected = getSelectedBook();
        return selected == null ? new ArrayList<>() : new ArrayList<>(List.of(selected));
    }

    private void updateLocalIconsGrid() {
        localIconsFlowPane.getChildren().clear();
        for (Book book : localFilteredList) {
            VBox card = new VBox(6);
            card.getStyleClass().add("book-card");
            card.setAlignment(Pos.CENTER);
            card.setUserData(book);

            if (selectedLocalBooks.contains(book)) {
                card.getStyleClass().add("book-card-selected");
            }

            ImageView cover = new ImageView();
            cover.setFitWidth(76);
            cover.setFitHeight(104);
            cover.setPreserveRatio(true);
            cover.setSmooth(true);
            cover.getStyleClass().add("book-card-cover");

            StackPane placeholder = AppIcons.createBookCoverPlaceholder(76, 104);

            StackPane coverStack = new StackPane(placeholder, cover);
            coverStack.setPrefSize(80, 108);
            coverStack.setAlignment(Pos.CENTER);
            loadThumbnailAsync(book, cover, placeholder);

            Label titleLabel = new Label(book.getTitle());
            titleLabel.getStyleClass().add("book-card-title");
            titleLabel.setWrapText(true);
            titleLabel.setMaxWidth(104);
            titleLabel.setAlignment(Pos.CENTER);
            titleLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
            Tooltip.install(titleLabel, new Tooltip(book.getTitle()));

            Label sizeLabel = new Label(book.getFormattedFileSize());
            sizeLabel.getStyleClass().add("book-card-size");

            card.getChildren().addAll(coverStack, titleLabel, sizeLabel);

            card.setOnMouseClicked(event -> {
                handleLocalCardSelection(book, event.isShortcutDown() || event.isShiftDown());
                if (event.getClickCount() == 2) {
                    openBookInReader(book);
                }
            });

            localIconsFlowPane.getChildren().add(card);
        }
    }

    private void selectDriveBook(Book book) {
        selectedDriveBooks.clear();
        if (book != null) {
            selectedDriveBooks.add(book);
        }
        selectedDriveBook = book;
        syncDriveSelectionToTable();
        updateDriveSelectionUI();
    }

    private void handleDriveCardSelection(Book book, boolean extendSelection) {
        if (book == null) return;
        if (extendSelection) {
            if (selectedDriveBooks.contains(book)) {
                selectedDriveBooks.remove(book);
            } else {
                selectedDriveBooks.add(book);
                selectedDriveBook = book;
            }
        } else {
            selectedDriveBooks.clear();
            selectedDriveBooks.add(book);
            selectedDriveBook = book;
        }
        if (!selectedDriveBooks.contains(selectedDriveBook)) {
            selectedDriveBook = selectedDriveBooks.stream().findFirst().orElse(null);
        }
        syncDriveSelectionToTable();
        updateDriveSelectionUI();
    }

    private void syncDriveSelectionToTable() {
        if (driveTableView == null) return;
        syncingDriveSelection = true;
        driveTableView.getSelectionModel().clearSelection();
        for (Book book : selectedDriveBooks) {
            driveTableView.getSelectionModel().select(book);
        }
        if (selectedDriveBook != null && selectedDriveBooks.contains(selectedDriveBook)) {
            driveTableView.getSelectionModel().select(selectedDriveBook);
        }
        syncingDriveSelection = false;
    }

    private void updateDriveSelectionUI() {
        if (selectedDriveBook == null || !selectedDriveBooks.contains(selectedDriveBook)) {
            selectedDriveBook = selectedDriveBooks.stream().findFirst().orElse(null);
        }
        for (javafx.scene.Node node : driveIconsFlowPane.getChildren()) {
            if (node instanceof VBox cardBox) {
                updateCardSelectionStyle(cardBox, selectedDriveBooks.contains(cardBox.getUserData()));
            }
        }
        updateActionButtonState();
    }

    private Book getSelectedDriveBook() {
        if (selectedDriveBook != null && selectedDriveBooks.contains(selectedDriveBook)) {
            return selectedDriveBook;
        }
        if (!selectedDriveBooks.isEmpty()) {
            return selectedDriveBooks.stream().findFirst().orElse(null);
        }
        Book tableSelected = driveTableView.getSelectionModel().getSelectedItem();
        return tableSelected != null ? tableSelected : selectedDriveBook;
    }

    private List<Book> getSelectedDriveBooks() {
        if (!selectedDriveBooks.isEmpty()) {
            return new ArrayList<>(selectedDriveBooks);
        }
        Book selected = getSelectedDriveBook();
        return selected == null ? new ArrayList<>() : new ArrayList<>(List.of(selected));
    }

    private void updateCardSelectionStyle(VBox cardBox, boolean selected) {
        if (selected) {
            if (!cardBox.getStyleClass().contains("book-card-selected")) {
                cardBox.getStyleClass().add("book-card-selected");
            }
        } else {
            cardBox.getStyleClass().remove("book-card-selected");
        }
    }

    private void updateDriveIconsGrid() {
        driveIconsFlowPane.getChildren().clear();
        for (Book book : driveFilteredList) {
            VBox card = new VBox(6);
            card.getStyleClass().add("book-card");
            card.setAlignment(Pos.CENTER);
            card.setUserData(book);

            if (selectedDriveBooks.contains(book)) {
                card.getStyleClass().add("book-card-selected");
            }

            ImageView cover = new ImageView();
            cover.setFitWidth(76);
            cover.setFitHeight(104);
            cover.setPreserveRatio(true);
            cover.setSmooth(true);
            cover.getStyleClass().add("book-card-cover");

            StackPane placeholder = AppIcons.createBookCoverPlaceholder(76, 104);
            StackPane coverNode = new StackPane(placeholder, cover);
            coverNode.setPrefSize(80, 108);
            coverNode.setAlignment(Pos.CENTER);
            loadDriveThumbnailAsync(book, cover, placeholder);

            Label titleLabel = new Label(book.getTitle());
            titleLabel.getStyleClass().add("book-card-title");
            titleLabel.setWrapText(true);
            titleLabel.setMaxWidth(104);
            titleLabel.setAlignment(Pos.CENTER);
            titleLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
            Tooltip.install(titleLabel, new Tooltip(book.getTitle()));

            Label sizeLabel = new Label(book.getFormattedFileSize());
            sizeLabel.getStyleClass().add("book-card-size");

            card.getChildren().addAll(coverNode, titleLabel, sizeLabel);

            card.setOnMouseClicked(event -> {
                handleDriveCardSelection(book, event.isShortcutDown() || event.isShiftDown());
                if (event.getClickCount() == 2) {
                    handleReadOnlineDriveBook(book);
                }
            });

            driveIconsFlowPane.getChildren().add(card);
        }
    }

    private void handleOpenSharedLinkInBrowser(String rawLink) {
        if (rawLink == null || rawLink.isBlank()) {
            showInfo("Shared Link Required", "Please enter or paste a Google Drive shared file or folder link.");
            return;
        }

        String link = rawLink.trim();
        if (!link.startsWith("http://") && !link.startsWith("https://")) {
            link = "https://drive.google.com/drive/folders/" + link;
        }

        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(java.net.URI.create(link));
                setStatus("Opened Google Drive link in web browser.");
            } else {
                showError("Browser Error", "Desktop browsing is not supported on this platform.");
            }
        } catch (Exception e) {
            showError("Browser Error", "Failed to open browser: " + e.getMessage());
        }
    }

    private void loadThumbnailAsync(Book book, ImageView imageView, Node fallbackIcon) {
        if (book == null) return;
        String path = book.getPath();
        if (path == null || !new File(path).exists()) return;

        if (fxThumbCache.containsKey(path)) {
            Image cached = fxThumbCache.get(path);
            imageView.setImage(cached);
            if (fallbackIcon != null) fallbackIcon.setVisible(false);
            return;
        }

        thumbExecutor.submit(() -> {
            try {
                java.awt.image.BufferedImage bImg = com.pdfreader.service.PdfService.getCoverThumbnail(new File(path));
                if (bImg != null) {
                    Image fxImg = SwingFXUtils.toFXImage(bImg, null);
                    fxThumbCache.put(path, fxImg);
                    Platform.runLater(() -> {
                        imageView.setImage(fxImg);
                        if (fallbackIcon != null) fallbackIcon.setVisible(false);
                    });
                }
            } catch (Exception ignored) {}
        });
    }

    private void loadDriveThumbnailAsync(Book book, ImageView imageView, Node fallbackIcon) {
        if (book == null) return;

        String path = book.getPath();
        if (path != null && new File(path).exists()) {
            loadThumbnailAsync(book, imageView, fallbackIcon);
            return;
        }

        String fileId = book.getDriveFileId();
        if (fileId == null || fileId.isBlank()) {
            fileId = book.getId();
        }
        if (fileId == null || fileId.isBlank()) {
            return;
        }

        String cacheKey = "drive:" + fileId;
        Image cached = driveThumbCache.get(cacheKey);
        if (cached != null) {
            if (!cached.isError()) {
                imageView.setImage(cached);
                if (fallbackIcon != null && cached.getProgress() >= 1.0) {
                    fallbackIcon.setVisible(false);
                }
            }
            return;
        }

        String thumbnailUrl = "https://drive.google.com/thumbnail?id=" + fileId + "&sz=w320";
        Image image = new Image(thumbnailUrl, 160, 220, true, true, true);
        driveThumbCache.put(cacheKey, image);
        imageView.setImage(image);

        image.progressProperty().addListener((obs, oldProgress, newProgress) -> {
            if (newProgress.doubleValue() >= 1.0 && !image.isError() && fallbackIcon != null) {
                fallbackIcon.setVisible(false);
            }
        });
        image.errorProperty().addListener((obs, wasError, isError) -> {
            if (isError) {
                imageView.setImage(null);
                if (fallbackIcon != null) {
                    fallbackIcon.setVisible(true);
                }
            }
        });
    }
}
