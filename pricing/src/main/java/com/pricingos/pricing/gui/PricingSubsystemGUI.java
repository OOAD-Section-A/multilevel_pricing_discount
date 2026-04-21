package com.pricingos.pricing.gui;

import com.jackfruit.scm.database.adapter.PackagingAdapter;
import com.jackfruit.scm.database.adapter.PricingAdapter;
import com.jackfruit.scm.database.facade.SupplyChainDatabaseFacade;
import com.jackfruit.scm.database.model.PriceList;
import com.jackfruit.scm.database.model.PackagingModels;
import com.jackfruit.scm.database.model.PricingModels;
import com.pricingos.common.ApprovalRequestType;
import com.pricingos.common.DiscountType;
import com.pricingos.common.IApproverRoleService;
import com.pricingos.common.IFloorPriceService;
import com.pricingos.common.ISkuCatalogService;
import com.pricingos.pricing.approval.ApprovalRequest;
import com.pricingos.pricing.approval.ApprovalRequestDao;
import com.pricingos.pricing.approval.ApprovalRoutingStrategy;
import com.pricingos.pricing.approval.ApprovalWorkflowEngine;
import com.pricingos.pricing.approval.AuditLogObserver;
import com.pricingos.pricing.approval.ProfitabilityAnalyticsObserver;
import com.pricingos.pricing.db.DaoBulk;
import com.pricingos.pricing.demo.PricingDemoDataSeeder;
import com.pricingos.pricing.exception.PricingExceptionReporter;
import com.pricingos.pricing.pricelist.PriceListManager;
import com.pricingos.pricing.promotion.InvalidPromoCodeException;
import com.pricingos.pricing.promotion.PromotionManager;
import com.pricingos.pricing.promotion.RebateProgramManager;
import com.pricingos.pricing.simulation.CurrencySimulator;
import com.pricingos.pricing.simulation.DynamicPricingEngine;
import com.pricingos.pricing.simulation.MarketPriceSimulator;
import com.pricingos.pricing.simulation.RegionalPricingService;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.RoundingMode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PricingSubsystemGUI extends JFrame {
    
    private static final Logger LOGGER = Logger.getLogger(PricingSubsystemGUI.class.getName());
    private static final Font TITLE_FONT = new Font("Segoe UI", Font.BOLD, 18);
    private static final Font BUTTON_FONT = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Font EMPHASIZED_BUTTON_FONT = new Font("Segoe UI", Font.BOLD, 12);
    private static final String DEFAULT_APPROVER_ID = resolveSetting(
        "pricing.default.approver.id", "PRICING_DEFAULT_APPROVER_ID", "pricing-manager");
    private static final String DEFAULT_ESCALATION_MANAGER_ID = resolveSetting(
        "pricing.default.escalation.approver.id", "PRICING_DEFAULT_ESCALATION_APPROVER_ID", "pricing-director");
    private static final String DEFAULT_REQUESTED_BY = resolveSetting(
        "pricing.default.requested.by", "PRICING_DEFAULT_REQUESTED_BY", "pricing-operator");
    private static final String DEFAULT_REJECTION_REASON = resolveSetting(
        "pricing.default.rejection.reason", "PRICING_DEFAULT_REJECTION_REASON", "Rejected during manual review.");

    private SupplyChainDatabaseFacade dbFacade;
    private PricingAdapter pricingAdapter;
    private PackagingAdapter packagingAdapter;
    private PromotionManager promotionManager;
    private PriceListManager priceListManager;
    private ApprovalWorkflowEngine approvalEngine;
    private AuditLogObserver auditLogObserver;
    private ProfitabilityAnalyticsObserver analyticsObserver;
    private RebateProgramManager rebateProgramManager;
    private PricingDemoDataSeeder demoDataSeeder;
    private DynamicPricingEngine dynamicPricingEngine;
    private CurrencySimulator currencySimulator;
    private RegionalPricingService regionalPricingService;
    private MarketPriceSimulator marketSimulator;
    private final List<String> pendingLogEntries = new ArrayList<>();
    
    private JTabbedPane tabbedPane;
    private JTextArea logArea;
    private JTable priceListTable;
    private JTable tierTable;
    private JTable promotionsTable;
    private JTextArea pricingCalculatorOutput;
    private DefaultTableModel priceTableModel;
    private DefaultTableModel tierTableModel;
    private DefaultTableModel promotionsTableModel;
    
    private DefaultTableModel approvalTableModel;
    private JTable approvalTable;
    private DefaultTableModel analyticsTableModel;
    private JTable analyticsTable;
    private JLabel totalRevenueDeltaLabel;
    private JTextArea rebateDetailArea;
    private JTextField rebateFilterCustomerField;
    private JTextArea rateArea;
    
    public PricingSubsystemGUI() {
        initializeDatabaseConnection();
        initializeSubsystemAPIs();
        initializeUI();
        if (shouldSeedDemoDataOnStartup()) {
            seedDemoData(false);
        } else {
            loadData();
        }
    }

    private void initializeSubsystemAPIs() {
        try {
            promotionManager = new PromotionManager(createSkuCatalogService());
            priceListManager = new PriceListManager();
            rebateProgramManager = new RebateProgramManager(pricingAdapter);
            marketSimulator = new MarketPriceSimulator();
            dynamicPricingEngine = new DynamicPricingEngine(marketSimulator);
            currencySimulator = new CurrencySimulator();
            regionalPricingService = new RegionalPricingService();
            approvalEngine = createApprovalEngine();
            auditLogObserver = new AuditLogObserver();
            analyticsObserver = new ProfitabilityAnalyticsObserver();
            approvalEngine.addObserver(auditLogObserver);
            approvalEngine.addObserver(analyticsObserver);
            demoDataSeeder = new PricingDemoDataSeeder(
                pricingAdapter,
                promotionManager,
                rebateProgramManager,
                approvalEngine,
                analyticsObserver,
                auditLogObserver,
                DEFAULT_REQUESTED_BY,
                DEFAULT_APPROVER_ID,
                DEFAULT_REJECTION_REASON,
                this::log);

            log("Initialized subsystem APIs: PromotionManager, RebateProgramManager, PriceListManager, ApprovalWorkflowEngine, AuditLogObserver, AnalyticsObserver");
        } catch (Exception e) {
            log("ERROR initializing subsystem APIs: " + e.getMessage());
            LOGGER.log(Level.SEVERE, "Failed to initialize subsystem APIs", e);
        }
    }

    private ISkuCatalogService createSkuCatalogService() {
        return new ISkuCatalogService() {
            @Override
            public boolean isSkuActive(String skuId) {
                return getAvailableSkuIds().contains(skuId);
            }

            @Override
            public List<String> getAllActiveSkuIds() {
                return getAvailableSkuIds();
            }
        };
    }

    private ApprovalWorkflowEngine createApprovalEngine() {
        ApprovalRoutingStrategy strategy = new ApprovalRoutingStrategy() {
            @Override
            public String resolveApproverId(ApprovalRequest request) {
                return DEFAULT_APPROVER_ID;
            }

            @Override
            public boolean requiresDualApproval(ApprovalRequest request) {
                return false;
            }
        };
        IApproverRoleService roleService = new IApproverRoleService() {
            @Override
            public boolean canApprove(String approverId, ApprovalRequestType type, double discount) {
                return true;
            }

            @Override
            public String getEscalationManagerId(String currentApprover) {
                return DEFAULT_ESCALATION_MANAGER_ID;
            }
        };

        ApprovalWorkflowEngine engine = new ApprovalWorkflowEngine(strategy, roleService);
        engine.withFloorPriceService(createFloorPriceService());
        return engine;
    }

    private IFloorPriceService createFloorPriceService() {
        return new IFloorPriceService() {
            @Override
            public boolean wouldViolateMargin(String pricingReference, double discountAmount) {
                PriceList price = findFloorPriceReference(pricingReference);
                if (price == null) {
                    return false;
                }
                BigDecimal maxAllowedDiscount = price.getBasePrice().subtract(price.getPriceFloor());
                return BigDecimal.valueOf(discountAmount).compareTo(maxAllowedDiscount) > 0;
            }

            @Override
            public double getEffectiveFloorPrice(String pricingReference) {
                PriceList price = findFloorPriceReference(pricingReference);
                if (price == null) {
                    throw new IllegalArgumentException(
                        "No active price found for pricing reference: " + pricingReference);
                }
                return price.getPriceFloor().doubleValue();
            }
        };
    }
    
    private void initializeDatabaseConnection() {
        try {
            log("Connecting to database OOAD...");
            dbFacade = new SupplyChainDatabaseFacade();
            pricingAdapter = new PricingAdapter(dbFacade);
            packagingAdapter = new PackagingAdapter(dbFacade);
            log("Database connection established successfully");
        } catch (Exception e) {
            log("ERROR: Failed to connect to database: " + e.getMessage());
            LOGGER.log(Level.SEVERE, "Database connection failed", e);
            JOptionPane.showMessageDialog(this,
                "Failed to connect to database: " + e.getMessage(),
                "Database Error",
                JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }
    
    private void initializeUI() {
        setTitle("SCM Pricing Subsystem - Multi-level Pricing & Discount Management");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1400, 900);
        setLocationRelativeTo(null);
        
        setLayout(new BorderLayout(10, 10));
        
        // Create header
        JPanel headerPanel = createHeaderPanel();
        add(headerPanel, BorderLayout.NORTH);
        
        // Create tabbed pane
        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 14));
        
        // Add tabs - both database and subsystem API functionality
        tabbedPane.addTab("Price List", createPriceListPanel());
        tabbedPane.addTab("Tier Definitions", createTierPanel());
        tabbedPane.addTab("Promotions (DB)", createPromotionsPanel());
        tabbedPane.addTab("Promo Code Manager", createPromoCodeManagerPanel());
        tabbedPane.addTab("Rebate Programs", createRebateProgramPanel());
        tabbedPane.addTab("Price Calculator", createPricingCalculatorPanel());
        tabbedPane.addTab("Approval Workflows", createApprovalPanel());
        tabbedPane.addTab("Profitability Analytics", createAnalyticsPanel());
        tabbedPane.addTab("Dynamic Pricing", createDynamicPricingPanel());
        tabbedPane.addTab("Currency Simulator", createCurrencyPanel());
        tabbedPane.addTab("Regional Pricing", createRegionalPanel());
        
        add(tabbedPane, BorderLayout.CENTER);
        
        // Create log panel
        JPanel logPanel = createLogPanel();
        add(logPanel, BorderLayout.SOUTH);
        
        setVisible(true);
    }

    private JLabel createSectionTitleLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(TITLE_FONT);
        return label;
    }

    private DefaultTableModel createReadOnlyTableModel(String[] columnNames) {
        return new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private JTable createReadOnlyTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setFont(BUTTON_FONT);
        table.setRowHeight(28);
        table.getTableHeader().setFont(EMPHASIZED_BUTTON_FONT);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        return table;
    }

    private JButton createButton(String text) {
        return createButton(text, false);
    }

    private JButton createButton(String text, boolean emphasized) {
        JButton button = new JButton(text);
        button.setFont(emphasized ? EMPHASIZED_BUTTON_FONT : BUTTON_FONT);
        return button;
    }

    private static String resolveSetting(String propertyName, String environmentVariable, String defaultValue) {
        String value = System.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            value = System.getenv(environmentVariable);
        }
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    private boolean shouldSeedDemoDataOnStartup() {
        return Boolean.parseBoolean(resolveSetting(
            "pricing.seed.demo",
            "PRICING_SEED_DEMO_DATA",
            "false"));
    }

    private int getSelectedModelRow(JTable table) {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return -1;
        }
        return table.convertRowIndexToModel(viewRow);
    }

    private void flushPendingLogEntries() {
        if (logArea == null || pendingLogEntries.isEmpty()) {
            return;
        }
        for (String pendingEntry : pendingLogEntries) {
            logArea.append(pendingEntry);
            logArea.append("\n");
        }
        pendingLogEntries.clear();
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void closeDatabaseFacade() {
        if (dbFacade == null) {
            return;
        }
        try {
            dbFacade.close();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to close database facade", e);
        } finally {
            dbFacade = null;
        }
    }

    @Override
    public void dispose() {
        closeDatabaseFacade();
        super.dispose();
    }

    private List<String> parseSkuList(String rawSkus) {
        if (rawSkus == null || rawSkus.isBlank()) {
            return List.of();
        }

        List<String> skuIds = new ArrayList<>();
        for (String token : rawSkus.split(",")) {
            String skuId = token.trim();
            if (!skuId.isEmpty()) {
                skuIds.add(skuId);
            }
        }
        return skuIds;
    }

    private int nextTierId() {
        int maxTierId = 0;
        for (int row = 0; row < tierTableModel.getRowCount(); row++) {
            Object value = tierTableModel.getValueAt(row, 0);
            if (value instanceof Number number) {
                maxTierId = Math.max(maxTierId, number.intValue());
            }
        }
        return maxTierId + 1;
    }

    private List<PriceList> getActivePrices() {
        if (pricingAdapter == null) {
            return List.of();
        }
        try {
            return pricingAdapter.getActivePrices();
        } catch (RuntimeException exception) {
            LOGGER.log(Level.FINE, "Failed to load active prices", exception);
            return List.of();
        }
    }

    private List<String> getAvailableSkuIds() {
        return getActivePrices().stream()
            .map(PriceList::getSkuId)
            .filter(skuId -> skuId != null && !skuId.isBlank())
            .distinct()
            .sorted()
            .toList();
    }

    private String defaultSkuInput() {
        List<String> skuIds = getAvailableSkuIds();
        return skuIds.isEmpty() ? "" : skuIds.get(0);
    }

    private String defaultEligibleSkuInput() {
        List<String> skuIds = getAvailableSkuIds();
        if (skuIds.isEmpty()) {
            return "";
        }
        return String.join(",", skuIds.subList(0, Math.min(2, skuIds.size())));
    }

    private String defaultCustomerInput() {
        return "";
    }

    private String defaultRegionInput() {
        return "GLOBAL";
    }

    private String defaultChannelInput() {
        return "RETAIL";
    }

    private String defaultTierName() {
        return "Tier-" + nextTierId();
    }

    private String buildPricingReference(String skuId) {
        return "SKU:" + skuId.trim();
    }

    private String extractSkuFromPricingReference(String pricingReference) {
        if (pricingReference == null) {
            return "";
        }
        String normalizedReference = pricingReference.trim();
        if (normalizedReference.startsWith("SKU:")) {
            return normalizedReference.substring("SKU:".length()).trim();
        }
        return normalizedReference;
    }

    private PriceList findActivePriceForSku(String skuId) {
        List<PriceList> activePrices = getActivePrices();
        if (activePrices.isEmpty()) {
            throw new IllegalArgumentException(
                "No active prices are configured. Add a price record in the Price List tab first.");
        }
        for (PriceList price : activePrices) {
            if (skuId.equals(price.getSkuId()) && "ACTIVE".equals(price.getStatus())) {
                return price;
            }
        }
        throw new IllegalArgumentException("No active base price found for SKU: " + skuId);
    }

    private PriceList findFloorPriceReference(String pricingReference) {
        String skuId = extractSkuFromPricingReference(pricingReference);
        if (skuId.isEmpty()) {
            return null;
        }
        try {
            return findActivePriceForSku(skuId);
        } catch (IllegalArgumentException exception) {
            LOGGER.log(Level.FINE, "No active price found for pricing reference " + pricingReference, exception);
            return null;
        }
    }
    
    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(70, 130, 180));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        
        JLabel titleLabel = new JLabel("Multi-level Pricing & Discount Management Subsystem");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        titleLabel.setForeground(Color.WHITE);
        
        JLabel subtitleLabel = new JLabel("Real-time Database Integration with OOAD Database");
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        subtitleLabel.setForeground(new Color(200, 220, 255));
        
        JPanel titlePanel = new JPanel(new GridLayout(2, 1));
        titlePanel.setOpaque(false);
        titlePanel.add(titleLabel);
        titlePanel.add(subtitleLabel);
        
        panel.add(titlePanel, BorderLayout.WEST);
        
        JButton seedButton = createButton("Seed Demo Data", true);
        seedButton.setBackground(Color.WHITE);
        seedButton.addActionListener(e -> seedDemoData(true));

        JButton refreshButton = createButton("Refresh All Data", true);
        refreshButton.setBackground(Color.WHITE);
        refreshButton.addActionListener(e -> {
            loadData();
            log("Data refreshed from database");
        });

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actionPanel.setOpaque(false);
        actionPanel.add(seedButton);
        actionPanel.add(refreshButton);

        panel.add(actionPanel, BorderLayout.EAST);
        
        return panel;
    }

    private void seedDemoData(boolean interactive) {
        if (demoDataSeeder == null) {
            log("ERROR seeding demo data: seeder is not initialized");
            if (interactive) {
                JOptionPane.showMessageDialog(this,
                    "Demo data seeder is not initialized.",
                    "Seed Error",
                    JOptionPane.ERROR_MESSAGE);
            }
            return;
        }

        try {
            PricingDemoDataSeeder.SeedReport report = demoDataSeeder.seed();
            if (interactive) {
                JOptionPane.showMessageDialog(this,
                    report.summary(),
                    "Demo Data Seeded",
                    JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception exception) {
            log("ERROR seeding demo data: " + exception.getMessage());
            LOGGER.log(Level.SEVERE, "Failed to seed demo data", exception);
            if (interactive) {
                JOptionPane.showMessageDialog(this,
                    "Error seeding demo data: " + exception.getMessage(),
                    "Seed Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        } finally {
            loadData();
        }
    }
    
    private JPanel createPriceListPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        panel.add(createSectionTitleLabel("Price List Management"), BorderLayout.NORTH);
        
        String[] columnNames = {"Price ID", "SKU ID", "Region", "Channel", "Price Type", 
                               "Base Price", "Floor Price", "Currency", "Status"};
        priceTableModel = createReadOnlyTableModel(columnNames);
        priceListTable = createReadOnlyTable(priceTableModel);
        
        JScrollPane scrollPane = new JScrollPane(priceListTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));

        JButton addPriceButton = createButton("Add Price");
        addPriceButton.addActionListener(e -> showAddPriceDialog());

        JButton deletePriceButton = createButton("Delete Price");
        deletePriceButton.addActionListener(e -> deleteSelectedPrice());

        JButton viewDetailsButton = createButton("View Details");
        viewDetailsButton.addActionListener(e -> showPriceDetails());

        buttonPanel.add(addPriceButton);
        buttonPanel.add(deletePriceButton);
        buttonPanel.add(viewDetailsButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private JPanel createTierPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        panel.add(createSectionTitleLabel("Customer Tier Definitions"), BorderLayout.NORTH);
        
        String[] columnNames = {"Tier ID", "Tier Name", "Min Spend Threshold", "Default Discount %"};
        tierTableModel = createReadOnlyTableModel(columnNames);
        tierTable = createReadOnlyTable(tierTableModel);
        
        JScrollPane scrollPane = new JScrollPane(tierTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        panel.add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));

        JButton addTierButton = createButton("Create Tier");
        addTierButton.addActionListener(e -> showAddTierDialog());

        buttonPanel.add(addTierButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }
    
    private JPanel createPromotionsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        panel.add(createSectionTitleLabel("Promotional & Seasonal Rule Engine"), BorderLayout.NORTH);
        
        String[] columnNames = {"Promo ID", "Promo Name", "Coupon Code", "Discount Type", 
                               "Discount Value", "Start Date", "End Date", "Min Cart Value", "Max Uses", "Current Uses"};
        promotionsTableModel = createReadOnlyTableModel(columnNames);
        promotionsTable = createReadOnlyTable(promotionsTableModel);
        
        JScrollPane scrollPane = new JScrollPane(promotionsTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }
    
    private JPanel createPromoCodeManagerPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        panel.add(createSectionTitleLabel("Promo Code Manager (Using PromotionManager API)"), BorderLayout.NORTH);

        // Create Promo Section
        JPanel createPanel = new JPanel(new GridLayout(0, 2, 10, 10));
        createPanel.setBorder(BorderFactory.createTitledBorder("Create New Promotion"));

        createPanel.add(new JLabel("Promotion Name:"));
        JTextField nameField = new JTextField("Seasonal Promotion");
        createPanel.add(nameField);

        createPanel.add(new JLabel("Coupon Code:"));
        JTextField codeField = new JTextField();
        createPanel.add(codeField);

        createPanel.add(new JLabel("Discount Type:"));
        JComboBox<String> typeCombo = new JComboBox<>(new String[]{"PERCENTAGE_OFF", "FIXED_AMOUNT", "BUY_X_GET_Y"});
        createPanel.add(typeCombo);

        createPanel.add(new JLabel("Discount Value:"));
        JTextField valueField = new JTextField("15.00");
        createPanel.add(valueField);

        createPanel.add(new JLabel("Eligible SKUs (comma-separated):"));
        JTextField skuField = new JTextField(defaultEligibleSkuInput());
        createPanel.add(skuField);

        createPanel.add(new JLabel("Min Cart Value:"));
        JTextField minCartField = new JTextField("0.00");
        createPanel.add(minCartField);

        createPanel.add(new JLabel("Max Uses (0 for unlimited):"));
        JTextField maxUsesField = new JTextField("100");
        createPanel.add(maxUsesField);

        createPanel.add(new JLabel("Start Date (YYYY-MM-DD):"));
        LocalDate today = LocalDate.now();
        JTextField startDateField = new JTextField(today.toString());
        createPanel.add(startDateField);

        createPanel.add(new JLabel("End Date (YYYY-MM-DD):"));
        LocalDate futureDate = today.plusMonths(6);
        JTextField endDateField = new JTextField(futureDate.toString());
        createPanel.add(endDateField);

        JButton createButton = createButton("Create Promotion", true);
        createButton.addActionListener(e -> {
            try {
                String name = nameField.getText();
                String couponCode = codeField.getText();
                String discountType = (String) typeCombo.getSelectedItem();
                double discountValue;
                List<String> eligibleSkus;
                double minCartValue;
                int maxUses;
                LocalDate startDate;
                LocalDate endDate;

                try {
                    discountValue = Double.parseDouble(valueField.getText());
                    minCartValue = Double.parseDouble(minCartField.getText());
                    maxUses = Integer.parseInt(maxUsesField.getText());
                } catch (NumberFormatException nfe) {
                    PricingExceptionReporter.unregistered(
                        "INVALID_NUMBER_INPUT",
                        "Promotion creation input validation failed: " + nfe.getMessage());
                    log("ERROR: Invalid numeric input for promotion creation");
                    return;
                }

                eligibleSkus = parseSkuList(skuField.getText());
                if (eligibleSkus.isEmpty()) {
                    throw new IllegalArgumentException("At least one eligible SKU is required.");
                }
                startDate = LocalDate.parse(startDateField.getText());
                endDate = LocalDate.parse(endDateField.getText());

                log("Creating promotion: " + name + " with code: " + couponCode + " for SKUs: " + eligibleSkus);
                log("Dates: " + startDate + " to " + endDate + ", Min cart: " + minCartValue);

                DiscountType type = DiscountType.valueOf(discountType);
                String promoId = promotionManager.createPromotion(name, couponCode, type, discountValue,
                    startDate, endDate, eligibleSkus, minCartValue, maxUses);

                log("Created promotion via PromotionManager: " + promoId + " - " + name);
                JOptionPane.showMessageDialog(this, "Promotion created successfully via PromotionManager API!\nID: " + promoId + "\nCoupon Code: " + couponCode,
                    "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                log("ERROR creating promotion: " + ex.getMessage());
                LOGGER.log(Level.SEVERE, "Failed to create promotion", ex);
                JOptionPane.showMessageDialog(this, "Error creating promotion: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JPanel createButtonPanel = new JPanel();
        createButtonPanel.add(createButton);

        // Validate Promo Section
        JPanel validatePanel = new JPanel(new GridLayout(0, 2, 10, 10));
        validatePanel.setBorder(BorderFactory.createTitledBorder("Validate Promo Code"));

        validatePanel.add(new JLabel("Coupon Code:"));
        JTextField validateCodeField = new JTextField();
        validatePanel.add(validateCodeField);

        validatePanel.add(new JLabel("SKU ID:"));
        JTextField validateSkuField = new JTextField(defaultSkuInput());
        validatePanel.add(validateSkuField);

        validatePanel.add(new JLabel("Cart Total:"));
        JTextField cartTotalField = new JTextField("100.00");
        validatePanel.add(cartTotalField);

        JButton validateButton = createButton("Validate & Get Discount", true);
        validateButton.addActionListener(e -> {
            String couponCode = validateCodeField.getText();
            String skuId = validateSkuField.getText();
            try {
                double cartTotal = Double.parseDouble(cartTotalField.getText());

                log("Validating promo code: " + couponCode + " for SKU: " + skuId + " with cart total: " + cartTotal);

                // Use PromotionManager API
                double discountAmount = promotionManager.validateAndGetDiscount(couponCode, skuId, cartTotal);

                log("Promo code valid! Discount: " + discountAmount);
                JOptionPane.showMessageDialog(this, "Promo code valid!\nDiscount Amount: " + discountAmount,
                    "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (InvalidPromoCodeException ex) {
                PricingExceptionReporter.invalidPromoCode(couponCode);
                return;
            } catch (Exception ex) {
                log("ERROR validating promo code: " + ex.getMessage());
                LOGGER.log(Level.SEVERE, "Failed to validate promotion", ex);
                JOptionPane.showMessageDialog(this, "Error validating promo code: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JPanel validateButtonPanel = new JPanel();
        validateButtonPanel.add(validateButton);

        // Active Promo Codes Section
        JPanel activePanel = new JPanel(new BorderLayout(10, 10));
        activePanel.setBorder(BorderFactory.createTitledBorder("Active Promo Codes"));

        JTextArea activePromoArea = new JTextArea(8, 40);
        activePromoArea.setEditable(false);
        activePromoArea.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        JButton refreshButton = createButton("Refresh Active Codes");
        refreshButton.addActionListener(e -> {
            try {
                List<String> activeCodes = promotionManager.getActivePromoCodes();
                activePromoArea.setText(String.join("\n", activeCodes));
                log("Loaded " + activeCodes.size() + " active promo codes from PromotionManager");
            } catch (Exception ex) {
                log("ERROR loading active promo codes: " + ex.getMessage());
            }
        });

        JPanel activeButtonPanel = new JPanel();
        activeButtonPanel.add(refreshButton);

        activePanel.add(activePromoArea, BorderLayout.CENTER);
        activePanel.add(activeButtonPanel, BorderLayout.SOUTH);

        // Layout
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.add(createPanel, BorderLayout.CENTER);
        topPanel.add(createButtonPanel, BorderLayout.SOUTH);

        JPanel middlePanel = new JPanel(new BorderLayout(10, 10));
        middlePanel.add(validatePanel, BorderLayout.CENTER);
        middlePanel.add(validateButtonPanel, BorderLayout.SOUTH);

        JSplitPane topSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topPanel, middlePanel);
        topSplitPane.setResizeWeight(0.5);

        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topSplitPane, activePanel);
        mainSplitPane.setResizeWeight(0.7);

        panel.add(mainSplitPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createRebateProgramPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        panel.add(createSectionTitleLabel("Rebate Program Manager (Volume-Based Rebates)"), BorderLayout.NORTH);

        // Create Rebate Section
        JPanel createPanel = new JPanel(new GridLayout(0, 2, 10, 10));
        createPanel.setBorder(BorderFactory.createTitledBorder("Create New Rebate Program"));

        createPanel.add(new JLabel("Customer ID:"));
        JTextField customerIdField = new JTextField(defaultCustomerInput());
        createPanel.add(customerIdField);

        createPanel.add(new JLabel("SKU ID:"));
        JTextField skuIdField = new JTextField(defaultSkuInput());
        createPanel.add(skuIdField);

        createPanel.add(new JLabel("Target Spend ($):"));
        JTextField targetSpendField = new JTextField("5000.00");
        createPanel.add(targetSpendField);

        createPanel.add(new JLabel("Rebate Percent (%):"));
        JTextField rebatePercentField = new JTextField("5.0");
        createPanel.add(rebatePercentField);

        JButton createButton = createButton("Create Rebate Program", true);
        createButton.addActionListener(e -> {
            try {
                String customerId = customerIdField.getText();
                String skuId = skuIdField.getText();
                double targetSpend = Double.parseDouble(targetSpendField.getText());
                double rebatePercent = Double.parseDouble(rebatePercentField.getText());

                String programId = rebateProgramManager.createRebateProgram(customerId, skuId, targetSpend, rebatePercent);
                log("Created rebate program: " + programId + " for customer " + customerId);
                JOptionPane.showMessageDialog(this, "Rebate program created successfully!\nID: " + programId,
                    "Success", JOptionPane.INFORMATION_MESSAGE);

                customerIdField.setText("");
                skuIdField.setText("");
                targetSpendField.setText("5000.00");
                rebatePercentField.setText("5.0");
                refreshRebatePrograms();
            } catch (NumberFormatException nfe) {
                log("ERROR: Invalid numeric input for rebate program creation");
                JOptionPane.showMessageDialog(this, "Error: Invalid numeric input. Please enter valid numbers.",
                    "Input Error", JOptionPane.ERROR_MESSAGE);
            } catch (Exception ex) {
                log("ERROR creating rebate program: " + ex.getMessage());
                JOptionPane.showMessageDialog(this, "Error creating rebate program: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JPanel createButtonPanel = new JPanel();
        createButtonPanel.add(createButton);

        // Record Purchase Section
        JPanel purchasePanel = new JPanel(new GridLayout(0, 2, 10, 10));
        purchasePanel.setBorder(BorderFactory.createTitledBorder("Record Purchase"));

        purchasePanel.add(new JLabel("Program ID:"));
        JTextField programIdField = new JTextField();
        purchasePanel.add(programIdField);

        purchasePanel.add(new JLabel("Purchase Amount ($):"));
        JTextField purchaseAmountField = new JTextField("1200.00");
        purchasePanel.add(purchaseAmountField);

        JButton recordButton = createButton("Record Purchase", true);
        recordButton.addActionListener(e -> {
            try {
                String programId = programIdField.getText();
                double purchaseAmount = Double.parseDouble(purchaseAmountField.getText());

                rebateProgramManager.recordPurchase(programId, purchaseAmount);
                log("Recorded purchase of $" + purchaseAmount + " for program " + programId);
                JOptionPane.showMessageDialog(this, "Purchase recorded successfully!",
                    "Success", JOptionPane.INFORMATION_MESSAGE);
                refreshRebatePrograms();
            } catch (NumberFormatException nfe) {
                log("ERROR: Invalid numeric input for purchase recording");
                JOptionPane.showMessageDialog(this, "Error: Invalid numeric input.",
                    "Input Error", JOptionPane.ERROR_MESSAGE);
            } catch (Exception ex) {
                log("ERROR recording purchase: " + ex.getMessage());
                JOptionPane.showMessageDialog(this, "Error recording purchase: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JPanel purchaseButtonPanel = new JPanel();
        purchaseButtonPanel.add(recordButton);

        // Active Rebate Programs Section
        JPanel activePanel = new JPanel(new BorderLayout(10, 10));
        activePanel.setBorder(BorderFactory.createTitledBorder("Active Rebate Programs (Filter by Customer)"));

        rebateDetailArea = new JTextArea(10, 80);
        rebateDetailArea.setEditable(false);
        rebateDetailArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        rebateDetailArea.setBackground(new Color(240, 245, 240));

        JButton refreshButton = createButton("Refresh Programs");
        rebateFilterCustomerField = new JTextField(15);
        rebateFilterCustomerField.setToolTipText("Enter a customer ID to filter rebate programs");
        
        refreshButton.addActionListener(e -> refreshRebatePrograms());

        JPanel refreshButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        refreshButtonPanel.add(new JLabel("Customer ID:"));
        refreshButtonPanel.add(rebateFilterCustomerField);
        refreshButtonPanel.add(refreshButton);

        activePanel.add(rebateDetailArea, BorderLayout.CENTER);
        activePanel.add(refreshButtonPanel, BorderLayout.SOUTH);

        // Layout
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.add(createPanel, BorderLayout.CENTER);
        topPanel.add(createButtonPanel, BorderLayout.SOUTH);

        JPanel middlePanel = new JPanel(new BorderLayout(10, 10));
        middlePanel.add(purchasePanel, BorderLayout.CENTER);
        middlePanel.add(purchaseButtonPanel, BorderLayout.SOUTH);

        JSplitPane topSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topPanel, middlePanel);
        topSplitPane.setResizeWeight(0.4);

        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topSplitPane, activePanel);
        mainSplitPane.setResizeWeight(0.6);

        panel.add(mainSplitPane, BorderLayout.CENTER);

        return panel;
    }

    private void refreshRebatePrograms() {
        try {
            log("Refreshing rebate programs from database...");
            
            StringBuilder display = new StringBuilder();
            display.append("ACTIVE REBATE PROGRAMS\n");
            display.append("======================\n\n");
            
            List<PricingModels.RebateProgram> programs = Collections.emptyList();
            String customerFilter = rebateFilterCustomerField != null ? rebateFilterCustomerField.getText().trim() : "";
            
            if (pricingAdapter != null) {
                if (!customerFilter.isEmpty()) {
                    programs = pricingAdapter.listRebateProgramsByCustomer(customerFilter);
                    display.append("Filtered for Customer ID: ").append(customerFilter).append("\n\n");
                } else {
                    display.append("Please enter a Customer ID in the filter box below to view active rebate programs.\n");
                    display.append("(The database adapter does not currently support listing ALL rebate programs globally.)\n\n");
                }
            }
            
            if (programs.isEmpty() && customerFilter.isEmpty()) {
                // Already handled above
            } else if (programs.isEmpty()) {
                display.append("No active rebate programs found for this customer.\n");
            } else {
                display.append(String.format("%-12s %-15s %-15s %-15s %-12s %-12s\n", 
                    "Program ID", "Customer", "SKU", "Progress", "Rebate Due", "Status"));
                display.append("-".repeat(82)).append("\n");
                
                for (PricingModels.RebateProgram prog : programs) {
                    String programId = prog.programId();
                    String customerId = prog.customerId();
                    String skuId = prog.skuId();
                    double targetSpend = prog.targetSpend().doubleValue();
                    double rebatePct = prog.rebatePct().doubleValue();
                    double accumulatedSpend = prog.accumulatedSpend().doubleValue();
                    
                    double progressPct = (accumulatedSpend / targetSpend) * 100.0;
                    boolean targetMet = accumulatedSpend >= targetSpend;
                    double rebateDue = targetMet ? accumulatedSpend * rebatePct : 0.0;
                    String status = targetMet ? "EARNED" : "PENDING";
                    
                    String progressStr = String.format("%.0f/%.0f (%.1f%%)", 
                        accumulatedSpend, targetSpend, progressPct);
                    
                    display.append(String.format("%-12s %-15s %-15s %-15s $%-11.2f %s\n",
                        programId, customerId, skuId, progressStr, rebateDue, status));
                    
                    display.append(String.format("  Target Met: %s | Rebate Rate: %.1f%% | Rebate Due: $%.2f\n\n",
                        targetMet ? "YES" : "NO", rebatePct, rebateDue));
                }
            }
            
            display.append("\n").append("-".repeat(82)).append("\n");
            
            if (rebateDetailArea != null) {
                rebateDetailArea.setText(display.toString());
                rebateDetailArea.setCaretPosition(0);
            }
            
            log("Loaded " + programs.size() + " rebate programs");
            
        } catch (Exception e) {
            log("ERROR refreshing rebate programs: " + e.getMessage());
            if (rebateDetailArea != null) {
                rebateDetailArea.setText("ERROR: " + e.getMessage());
            }
        }
    }

    private JPanel createPricingCalculatorPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        panel.add(createSectionTitleLabel("Pricing Calculator"), BorderLayout.NORTH);
        
        JPanel inputPanel = new JPanel(new GridLayout(0, 2, 10, 10));
        inputPanel.setBorder(BorderFactory.createTitledBorder("Input"));
        
        inputPanel.add(new JLabel("SKU:"));
        JTextField skuField = new JTextField(defaultSkuInput());
        inputPanel.add(skuField);
        
        inputPanel.add(new JLabel("Customer ID:"));
        JTextField customerField = new JTextField(defaultCustomerInput());
        inputPanel.add(customerField);
        
        inputPanel.add(new JLabel("Quantity:"));
        JTextField quantityField = new JTextField("10");
        inputPanel.add(quantityField);
        
        inputPanel.add(new JLabel("Promo Code:"));
        JTextField promoField = new JTextField();
        inputPanel.add(promoField);
        
        // Add dropdown to load rebate programs
        inputPanel.add(new JLabel("Load Rebate Program:"));
        JComboBox<String> rebateCombo = new JComboBox<>();
        rebateCombo.addItem("-- Select to Auto-Fill --");
        inputPanel.add(rebateCombo);
        
        JButton calculateButton = createButton("Calculate Price", true);
        calculateButton.addActionListener(e -> calculatePrice(skuField, customerField, quantityField, promoField));
        
        JButton refreshRebatesBtn = createButton("Refresh Rebate List");
        refreshRebatesBtn.addActionListener(e -> {
            String customerId = customerField.getText();
            rebateCombo.removeAllItems();
            rebateCombo.addItem("-- Select to Auto-Fill --");
            if (pricingAdapter != null && customerId != null && !customerId.trim().isEmpty()) {
                List<PricingModels.RebateProgram> programs = pricingAdapter.listRebateProgramsByCustomer(customerId.trim());
                for (PricingModels.RebateProgram prog : programs) {
                    try {
                        String custId = prog.customerId();
                        String sku = prog.skuId();
                        String progId = prog.programId();
                        
                        String displayText = progId + " (" + custId + " / " + sku + ")";
                        rebateCombo.addItem(displayText + "|" + custId + "|" + sku);
                    } catch (Exception ex) {
                        // skip
                    }
                }
                log("Rebate program list refreshed (from database) for " + customerId);
            } else {
                log("Please enter a Customer ID to fetch their rebate programs.");
            }
        });
        
        rebateCombo.addActionListener(e -> {
            String selected = (String) rebateCombo.getSelectedItem();
            if (selected != null && selected.contains("|")) {
                String[] parts = selected.split("\\|");
                if (parts.length == 3) {
                    customerField.setText(parts[1]);
                    skuField.setText(parts[2]);
                    log("Auto-filled customer and SKU from rebate program");
                }
            }
        });
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        buttonPanel.add(calculateButton);
        buttonPanel.add(refreshRebatesBtn);
        
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.add(inputPanel, BorderLayout.CENTER);
        topPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        // Output area
        pricingCalculatorOutput = new JTextArea(10, 50);
        pricingCalculatorOutput.setEditable(false);
        pricingCalculatorOutput.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        pricingCalculatorOutput.setBackground(new Color(245, 245, 250));
        
        JScrollPane outputScrollPane = new JScrollPane(pricingCalculatorOutput);
        outputScrollPane.setBorder(BorderFactory.createTitledBorder("Pricing Breakdown"));
        
        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(outputScrollPane, BorderLayout.CENTER);
        
        return panel;
    }

    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(Color.GRAY, 1),
            "System Log",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            new Font("Segoe UI", Font.PLAIN, 12)
        ));
        
        logArea = new JTextArea(6, 50);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        logArea.setBackground(new Color(240, 240, 240));
        logArea.setForeground(new Color(50, 50, 50));
        
        JScrollPane scrollPane = new JScrollPane(logArea);
        panel.add(scrollPane, BorderLayout.CENTER);
        flushPendingLogEntries();
        
        return panel;
    }
    
    private void loadData() {
        loadPriceList();
        loadTierDefinitions();
        loadPromotions();
        loadApprovals();
        loadAnalytics();
        refreshRebatePrograms();
    }
    
    private void loadPriceList() {
        try {
            log("Loading price list from database...");
            priceTableModel.setRowCount(0);
            
            List<PriceList> prices = getActivePrices();
            for (PriceList price : prices) {
                Object[] row = {
                    price.getPriceId(),
                    price.getSkuId(),
                    price.getRegionCode(),
                    price.getChannel(),
                    price.getPriceType(),
                    price.getBasePrice(),
                    price.getPriceFloor(),
                    price.getCurrencyCode(),
                    price.getStatus()
                };
                priceTableModel.addRow(row);
            }
            
            log("Loaded " + prices.size() + " active price records");
            if (prices.isEmpty()) {
                log("No active prices found. Add a price in the Price List tab before using price calculations.");
            }
        } catch (Exception e) {
            log("ERROR loading price list: " + e.getMessage());
            LOGGER.log(Level.SEVERE, "Failed to load price list", e);
        }
    }
    
    private void loadTierDefinitions() {
        try {
            log("Loading tier definitions from database...");
            tierTableModel.setRowCount(0);
            
            List<PricingModels.TierDefinition> tiers = pricingAdapter.listAllTierDefinitions();
            for (PricingModels.TierDefinition tier : tiers) {
                Object[] row = {
                    tier.tierId(),
                    tier.tierName(),
                    tier.minSpendThreshold(),
                    tier.defaultDiscountPct()
                };
                tierTableModel.addRow(row);
            }
            
            log("Loaded " + tiers.size() + " tier definitions");
        } catch (Exception e) {
            log("ERROR loading tier definitions: " + e.getMessage());
            LOGGER.log(Level.SEVERE, "Failed to load tier definitions", e);
        }
    }
    
    private void loadPromotions() {
        try {
            log("Loading promotions from database...");
            promotionsTableModel.setRowCount(0);

            List<PackagingModels.PackagingPromotion> promotions = packagingAdapter.listPromotions();
            for (PackagingModels.PackagingPromotion promo : promotions) {
                Object[] row = {
                    promo.promoId(),
                    promo.promoName(),
                    promo.couponCode(),
                    promo.discountType(),
                    promo.discountValue(),
                    promo.startDate(),
                    promo.endDate(),
                    promo.minCartValue(),
                    promo.maxUses(),
                    promo.currentUseCount()
                };
                promotionsTableModel.addRow(row);
            }

            log("Loaded " + promotions.size() + " promotions");
        } catch (Exception e) {
            log("ERROR loading promotions: " + e.getMessage());
            LOGGER.log(Level.SEVERE, "Failed to load promotions", e);
        }
    }
    
    private void showAddPriceDialog() {
        JDialog dialog = new JDialog(this, "Add New Price", true);
        dialog.setSize(500, 400);
        dialog.setLocationRelativeTo(this);
        
        JPanel panel = new JPanel(new GridLayout(0, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        panel.add(new JLabel("SKU ID:"));
        JTextField skuField = new JTextField();
        panel.add(skuField);
        
        panel.add(new JLabel("Region:"));
        JTextField regionField = new JTextField(defaultRegionInput());
        panel.add(regionField);
        
        panel.add(new JLabel("Channel:"));
        JTextField channelField = new JTextField(defaultChannelInput());
        panel.add(channelField);
        
        panel.add(new JLabel("Price Type:"));
        JComboBox<String> typeCombo = new JComboBox<>(new String[]{"RETAIL", "DISTRIBUTOR"});
        panel.add(typeCombo);
        
        panel.add(new JLabel("Base Price:"));
        JTextField basePriceField = new JTextField("150.00");
        panel.add(basePriceField);
        
        panel.add(new JLabel("Floor Price:"));
        JTextField floorPriceField = new JTextField("120.00");
        panel.add(floorPriceField);
        
        panel.add(new JLabel("Currency:"));
        JTextField currencyField = new JTextField("INR");
        panel.add(currencyField);
        
        JButton addButton = createButton("Add Price", true);
        addButton.addActionListener(e -> {
            try {
                String priceId = "PRICE-" + System.currentTimeMillis();
                String skuId = skuField.getText();
                String region = regionField.getText();
                String channel = channelField.getText();
                String priceType = (String) typeCombo.getSelectedItem();
                BigDecimal basePrice = new BigDecimal(basePriceField.getText());
                BigDecimal floorPrice = new BigDecimal(floorPriceField.getText());
                String currency = currencyField.getText();
                
                // Create PriceList object and publish it
                PriceList newPrice = new PriceList();
                newPrice.setPriceId(priceId);
                newPrice.setSkuId(skuId);
                newPrice.setRegionCode(region);
                newPrice.setChannel(channel);
                newPrice.setPriceType(priceType);
                newPrice.setBasePrice(basePrice);
                newPrice.setPriceFloor(floorPrice);
                newPrice.setCurrencyCode(currency);
                newPrice.setStatus("ACTIVE");
                newPrice.setEffectiveFrom(LocalDateTime.now());
                newPrice.setEffectiveTo(LocalDateTime.now().plusMonths(1));
                
                pricingAdapter.publishPrice(newPrice);
                
                log("Added new price: " + priceId + " for SKU: " + skuId);
                loadData();
                dialog.dispose();
                
                JOptionPane.showMessageDialog(this, "Price added successfully!",
                    "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                log("ERROR adding price: " + ex.getMessage());
                JOptionPane.showMessageDialog(this, "Error adding price: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(addButton);
        
        dialog.setLayout(new BorderLayout());
        dialog.add(panel, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }
    
    private void showPriceDetails() {
        int selectedRow = getSelectedModelRow(priceListTable);
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a price record first",
                "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        String priceId = (String) priceTableModel.getValueAt(selectedRow, 0);
        String skuId = (String) priceTableModel.getValueAt(selectedRow, 1);
        String region = (String) priceTableModel.getValueAt(selectedRow, 2);
        String channel = (String) priceTableModel.getValueAt(selectedRow, 3);
        String priceType = (String) priceTableModel.getValueAt(selectedRow, 4);
        BigDecimal basePrice = (BigDecimal) priceTableModel.getValueAt(selectedRow, 5);
        BigDecimal floorPrice = (BigDecimal) priceTableModel.getValueAt(selectedRow, 6);
        String currency = (String) priceTableModel.getValueAt(selectedRow, 7);
        String status = (String) priceTableModel.getValueAt(selectedRow, 8);
        
        String message = String.format(
            "Price Details:\n\n" +
            "Price ID: %s\n" +
            "SKU ID: %s\n" +
            "Region: %s\n" +
            "Channel: %s\n" +
            "Price Type: %s\n" +
            "Base Price: %s %s\n" +
            "Floor Price: %s %s\n" +
            "Status: %s\n",
            priceId, skuId, region, channel, priceType,
            basePrice, currency, floorPrice, currency, status
        );
        
        JOptionPane.showMessageDialog(this, message, "Price Details", JOptionPane.INFORMATION_MESSAGE);
    }

    private void deleteSelectedPrice() {
        int selectedRow = getSelectedModelRow(priceListTable);
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a price record to delete",
                "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String priceId = (String) priceTableModel.getValueAt(selectedRow, 0);
        int confirm = JOptionPane.showConfirmDialog(this,
            "Are you sure you want to delete price: " + priceId + "?",
            "Confirm Delete", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                priceListManager.deletePrice(priceId);
                log("Successfully deleted price: " + priceId);
                JOptionPane.showMessageDialog(this,
                    "Price deleted successfully: " + priceId,
                    "Deleted", JOptionPane.INFORMATION_MESSAGE);
                loadData();
            } catch (Exception e) {
                log("ERROR deleting price: " + e.getMessage());
                JOptionPane.showMessageDialog(this, "Error deleting price: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void showAddTierDialog() {
        JDialog dialog = new JDialog(this, "Create Tier Definition", true);
        dialog.setSize(400, 300);
        dialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel(new GridLayout(0, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        panel.add(new JLabel("Tier Name:"));
        JTextField nameField = new JTextField(defaultTierName());
        panel.add(nameField);

        panel.add(new JLabel("Min Spend Threshold:"));
        JTextField thresholdField = new JTextField("100000.00");
        panel.add(thresholdField);

        panel.add(new JLabel("Default Discount %:"));
        JTextField discountField = new JTextField("20.00");
        panel.add(discountField);

        JButton addButton = createButton("Create Tier", true);
        addButton.addActionListener(e -> {
            try {
                Integer tierId = nextTierId();
                String tierName = nameField.getText();
                BigDecimal minSpendThreshold = new BigDecimal(thresholdField.getText());
                BigDecimal defaultDiscountPct = new BigDecimal(discountField.getText());

                PricingModels.TierDefinition tier = new PricingModels.TierDefinition(
                    tierId, tierName, minSpendThreshold, defaultDiscountPct
                );

                pricingAdapter.createTierDefinition(tier);

                log("Created tier: " + tierName + " with ID: " + tierId);
                loadData();
                dialog.dispose();

                JOptionPane.showMessageDialog(this, "Tier created successfully!",
                    "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                log("ERROR creating tier: " + ex.getMessage());
                JOptionPane.showMessageDialog(this, "Error creating tier: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(addButton);

        dialog.setLayout(new BorderLayout());
        dialog.add(panel, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }
    private void loadApprovals() {
        try {
            log("Loading approval requests from database...");
            approvalTableModel.setRowCount(0);
            List<ApprovalRequest> requests = ApprovalRequestDao.findAll(null);
            for (ApprovalRequest request : requests) {
                approvalTableModel.addRow(new Object[]{ 
                    request.getApprovalId(), 
                    request.getOrderId(), 
                    request.getRequestType().name(),
                    request.getRequestedBy(), 
                    request.getRequestedDiscountAmt(), 
                    request.getJustificationText(),
                    request.getRoutedToApproverId(),
                    request.getSubmissionTime(), 
                    request.getStatus().name() 
                });
            }
            log("Loaded " + requests.size() + " approval requests");
        } catch (Exception e) { log("ERROR loading approvals: " + e.getMessage()); }
    }

    private void loadAnalytics() {
        try {
            log("Loading profitability analytics from database...");
            analyticsTableModel.setRowCount(0);
            List<ProfitabilityAnalyticsObserver.ProfitabilityEntry> entries = analyticsObserver.getAllRecords();
            for (ProfitabilityAnalyticsObserver.ProfitabilityEntry entry : entries) {
                analyticsTableModel.addRow(new Object[]{
                    entry.approvalId(),
                    entry.requestType().name(),
                    entry.finalStatus().name(),
                    entry.discountAmount(),
                    entry.recordedAt()
                });
            }
            totalRevenueDeltaLabel.setText("Total Revenue Impact (Approved Discounts): " + analyticsObserver.getApprovedRevenueDelta());
            log("Loaded " + entries.size() + " profitability analytics entries");
        } catch (Exception e) { log("ERROR loading analytics: " + e.getMessage()); }
    }

    private JPanel createApprovalPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        panel.add(createSectionTitleLabel("Pending Approval Requests - Workflow Management"), BorderLayout.NORTH);
        
        String[] columns = {"ID", "Reference", "Type", "Requested By", "Discount", "Justification", "Assigned To", "Submission Time", "Status"};
        approvalTableModel = createReadOnlyTableModel(columns);
        approvalTable = createReadOnlyTable(approvalTableModel);
        
        approvalTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    int row = getSelectedModelRow(approvalTable);
                    if (row >= 0) {
                        String id = (String) approvalTableModel.getValueAt(row, 0);
                        showApprovalDetails(id);
                    }
                }
            }
        });
        
        panel.add(new JScrollPane(approvalTable), BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        
        JButton detailsBtn = createButton("View Details");
        detailsBtn.addActionListener(e -> {
            int row = getSelectedModelRow(approvalTable);
            if (row >= 0) {
                String id = (String) approvalTableModel.getValueAt(row, 0);
                showApprovalDetails(id);
            } else { JOptionPane.showMessageDialog(panel, "Select a request first", "No Selection", JOptionPane.WARNING_MESSAGE); }
        });
        
        JButton approveBtn = createButton("Approve Request", true);
        approveBtn.addActionListener(e -> {
            int row = getSelectedModelRow(approvalTable);
            if (row >= 0) {
                String id = (String) approvalTableModel.getValueAt(row, 0);
                try {
                	approvalEngine.approve(id, DEFAULT_APPROVER_ID);
                	loadData();
                	JOptionPane.showMessageDialog(panel, "Request Approved Successfully!");
                } catch(Exception ex) { JOptionPane.showMessageDialog(panel, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE); }
            } else { JOptionPane.showMessageDialog(panel, "Select a request first", "No Selection", JOptionPane.WARNING_MESSAGE); }
        });
        
        JButton rejectBtn = createButton("Reject Request", true);
        rejectBtn.addActionListener(e -> {
            int row = getSelectedModelRow(approvalTable);
            if (row >= 0) {
                String id = (String) approvalTableModel.getValueAt(row, 0);
                JDialog rejectDialog = new JDialog(this, "Reject Approval Request", true);
                rejectDialog.setSize(500, 250);
                rejectDialog.setLocationRelativeTo(this);
                
                JPanel content = new JPanel(new BorderLayout(10, 10));
                content.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
                
                JLabel label = new JLabel("Enter rejection reason:");
                JTextArea reasonArea = new JTextArea(6, 40);
                reasonArea.setLineWrap(true);
                reasonArea.setWrapStyleWord(true);
                reasonArea.setText(DEFAULT_REJECTION_REASON);
                
                JButton submitBtn = createButton("Reject with Reason", true);
                submitBtn.addActionListener(ae -> {
                    try {
                        String reason = reasonArea.getText().trim();
                        if (reason.isEmpty()) {
                            reason = DEFAULT_REJECTION_REASON;
                        }
                        approvalEngine.reject(id, DEFAULT_APPROVER_ID, reason);
                        loadData();
                        rejectDialog.dispose();
                        JOptionPane.showMessageDialog(panel, "Request rejected!\nReason: " + reason);
                    } catch(Exception ex) { 
                        JOptionPane.showMessageDialog(rejectDialog, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE); 
                    }
                });
                
                content.add(label, BorderLayout.NORTH);
                content.add(new JScrollPane(reasonArea), BorderLayout.CENTER);
                content.add(submitBtn, BorderLayout.SOUTH);
                
                rejectDialog.setLayout(new BorderLayout());
                rejectDialog.add(content);
                rejectDialog.setVisible(true);
            } else { JOptionPane.showMessageDialog(panel, "Select a request first", "No Selection", JOptionPane.WARNING_MESSAGE); }
        });
        
        JButton escalateBtn = createButton("Escalate Stale Requests");
        escalateBtn.addActionListener(e -> {
            try {
                approvalEngine.escalateStaleRequests();
                loadData();
                JOptionPane.showMessageDialog(panel, "All stale requests were evaluated for escalation.");
            } catch(Exception ex) { 
                JOptionPane.showMessageDialog(panel, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE); 
            }
        });
        
        buttonPanel.add(detailsBtn);
        buttonPanel.add(approveBtn);
        buttonPanel.add(rejectBtn);
        buttonPanel.add(escalateBtn);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        return panel;
    }
    
    private void showApprovalDetails(String approvalId) {
        try {
            JDialog detailsDialog = new JDialog(this, "Approval Request Details", true);
            detailsDialog.setSize(600, 500);
            detailsDialog.setLocationRelativeTo(this);
            
            JPanel content = new JPanel(new BorderLayout(10, 10));
            content.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
            
            ApprovalRequest request = approvalEngine.getRequestById(approvalId);
            
            StringBuilder details = new StringBuilder();
            details.append("APPROVAL REQUEST DETAILS\n");
            details.append("========================\n\n");
            details.append("Request ID: ").append(request.getApprovalId()).append("\n");
            details.append("Reference ID: ").append(request.getOrderId()).append("\n");
            details.append("Request Type: ").append(request.getRequestType().name()).append("\n");
            details.append("Status: ").append(request.getStatus().name()).append("\n\n");
            
            details.append("Requested By: ").append(request.getRequestedBy()).append("\n");
            details.append("Submission Time: ").append(request.getSubmissionTime()).append("\n");
            details.append("Routed To: ").append(request.getRoutedToApproverId()).append("\n\n");
            
            details.append("Requested Discount Amount: $").append(String.format("%.2f", request.getRequestedDiscountAmt())).append("\n\n");
            
            details.append("JUSTIFICATION\n");
            details.append("-------------\n");
            details.append(request.getJustificationText()).append("\n\n");
            
            if (request.getApprovalTimestamp() != null) {
                details.append("Approved By: ").append(request.getApprovingManagerId()).append("\n");
                details.append("Approval Time: ").append(request.getApprovalTimestamp()).append("\n");
            }
            
            if (request.getRejectionReason() != null) {
                details.append("Rejection Reason: ").append(request.getRejectionReason()).append("\n");
            }
            
            if (request.getEscalationTime() != null) {
                details.append("Escalation Time: ").append(request.getEscalationTime()).append("\n");
            }
            
            details.append("\n-----------------------------------\n");
            details.append("Audit Log:\n");
            for (AuditLogObserver.AuditEntry audit : DaoBulk.AuditLogDao.findAll()) {
                if (audit.approvalId().equals(approvalId)) {
                    details.append("  [").append(audit.timestamp()).append("] ");
                    details.append(audit.eventType()).append(" by ").append(audit.actor()).append(": ");
                    details.append(audit.detail()).append("\n");
                }
            }
            
            JTextArea textArea = new JTextArea(details.toString());
            textArea.setEditable(false);
            textArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
            textArea.setBackground(new Color(240, 245, 250));
            
            JButton closeBtn = createButton("Close");
            closeBtn.addActionListener(e -> detailsDialog.dispose());
            
            content.add(new JScrollPane(textArea), BorderLayout.CENTER);
            content.add(closeBtn, BorderLayout.SOUTH);
            
            detailsDialog.setLayout(new BorderLayout());
            detailsDialog.add(content);
            detailsDialog.setVisible(true);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error loading details: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JPanel createAnalyticsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        JPanel header = new JPanel(new BorderLayout());
        header.add(createSectionTitleLabel("Profitability Analytics (Decisions Ledger)"), BorderLayout.NORTH);
        totalRevenueDeltaLabel = new JLabel("Total Revenue Impact: 0.0");
        totalRevenueDeltaLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        header.add(totalRevenueDeltaLabel, BorderLayout.SOUTH);
        panel.add(header, BorderLayout.NORTH);
        
        String[] columns = {"Approval ID", "Request Type", "Final Status", "Discount Amount", "Recorded At"};
        analyticsTableModel = createReadOnlyTableModel(columns);
        analyticsTable = createReadOnlyTable(analyticsTableModel);
        panel.add(new JScrollPane(analyticsTable), BorderLayout.CENTER);
        
        return panel;
    }

    private void calculatePrice(JTextField skuField, JTextField customerField,
                               JTextField quantityField, JTextField promoField) {
        try {
            String skuId = skuField.getText();
            String customerId = customerField.getText();
            int quantity = Integer.parseInt(quantityField.getText());
            String promoCode = promoField.getText();
            if (skuId == null || skuId.isBlank()) {
                throw new IllegalArgumentException("Enter a SKU with an active price record.");
            }
            if (quantity <= 0) {
                throw new IllegalArgumentException("Quantity must be greater than zero.");
            }
            
            StringBuilder breakdown = new StringBuilder();
            breakdown.append("=== PRICING CALCULATION ===\n\n");
            
            PriceList activePrice = findActivePriceForSku(skuId.trim());
            skuId = activePrice.getSkuId();
            BigDecimal basePrice = activePrice.getBasePrice();
            breakdown.append("Base Price: ").append(basePrice).append(" ").append(activePrice.getCurrencyCode()).append("\n");
            
            BigDecimal subtotal = basePrice.multiply(BigDecimal.valueOf(quantity));
            breakdown.append("Subtotal (").append(quantity).append(" units): ").append(subtotal).append("\n\n");
            
            // Step 2: Apply Tier Discount
            List<PricingModels.TierDefinition> tiers = pricingAdapter.listAllTierDefinitions();
            BigDecimal tierDiscount = BigDecimal.ZERO;
            String tierName = "Standard";
            
            // Simulate tier lookup based on customer spend (simplified)
            for (PricingModels.TierDefinition tier : tiers) {
                if (subtotal.compareTo(tier.minSpendThreshold()) >= 0) {
                    tierDiscount = tier.defaultDiscountPct();
                    tierName = tier.tierName();
                }
            }
            
            BigDecimal tierDiscountAmount = subtotal.multiply(tierDiscount).divide(BigDecimal.valueOf(100));
            BigDecimal afterTier = subtotal.subtract(tierDiscountAmount);
            breakdown.append("Tier Discount (").append(tierName).append("): -").append(tierDiscountAmount)
                     .append(" (").append(tierDiscount).append("%)\n");
            breakdown.append("After Tier: ").append(afterTier).append("\n\n");
            
            // Step 3: Apply Promo Discount using PromotionManager API
            BigDecimal promoDiscount = BigDecimal.ZERO;
            try {
                double discountAmount = promotionManager.validateAndGetDiscount(promoCode, skuId, afterTier.doubleValue());
                promoDiscount = BigDecimal.valueOf(discountAmount);
                breakdown.append("Promo Applied via PromotionManager: ").append(promoCode).append("\n");
            } catch (InvalidPromoCodeException ex) {
                breakdown.append("Promo Code Invalid: ").append(ex.getReason()).append("\n");
                promoDiscount = BigDecimal.ZERO;
            }

            BigDecimal afterPromo = afterTier.subtract(promoDiscount);
            breakdown.append("Promo Discount: -").append(promoDiscount).append("\n");
            breakdown.append("After Promo: ").append(afterPromo).append("\n\n");
            
            // Step 3B: Check Rebate Eligibility
            breakdown.append("=== REBATE PROGRAM STATUS ===\n");
            
            List<PricingModels.RebateProgram> rebatePrograms = Collections.emptyList();
            if (pricingAdapter != null && customerId != null && !customerId.isEmpty()) {
                rebatePrograms = pricingAdapter.listRebateProgramsByCustomer(customerId.trim());
            }
            boolean foundRebateProgram = false;
            
            for (PricingModels.RebateProgram prog : rebatePrograms) {
                try {
                    String progCustomerId = prog.customerId();
                    String progSkuId = prog.skuId();
                    String programId = prog.programId();
                    double targetSpend = prog.targetSpend().doubleValue();
                    double rebatePct = prog.rebatePct().doubleValue();
                    double accumulatedSpend = prog.accumulatedSpend().doubleValue();                    
                    if (progCustomerId.equals(customerId) && progSkuId.equals(skuId)) {
                        foundRebateProgram = true;
                        breakdown.append("Rebate Program Found: ").append(programId).append("\n");
                        breakdown.append("  Target Spend: $").append(String.format("%.2f", targetSpend)).append("\n");
                        breakdown.append("  Accumulated: $").append(String.format("%.2f", accumulatedSpend)).append("\n");
                        breakdown.append("  Rebate Rate: ").append(String.format("%.1f", rebatePct)).append("%\n");
                        
                        double progressPct = (accumulatedSpend / targetSpend) * 100.0;
                        breakdown.append("  Progress: ").append(String.format("%.1f%%", progressPct)).append("\n");
                        
                        if (accumulatedSpend >= targetSpend) {
                            double rebateDue = accumulatedSpend * (rebatePct / 100.0);
                            breakdown.append("  Status: TARGET MET - Rebate Due: $").append(String.format("%.2f", rebateDue)).append("\n\n");
                        } else {
                            double stillNeeded = targetSpend - accumulatedSpend;
                            breakdown.append("  Status: Pending (Still need: $").append(String.format("%.2f", stillNeeded)).append(")\n\n");
                        }
                    }
                } catch (Exception ignored) {}
            }
            
            if (!foundRebateProgram) {
                breakdown.append("No active rebate program for this customer/SKU combination.\n\n");
            }
            
            // Step 4: Apply Policy Rules (simplified)
            BigDecimal policyDiscount = BigDecimal.ZERO;
            if (afterPromo.compareTo(new BigDecimal("1000")) > 0) {
                policyDiscount = new BigDecimal("5.00");
                breakdown.append("Volume Policy: -").append(policyDiscount).append(" (5%)\n");
            }
            
            BigDecimal finalPrice = afterPromo.subtract(afterPromo.multiply(policyDiscount).divide(BigDecimal.valueOf(100)));
            breakdown.append("Final Price: ").append(finalPrice).append("\n\n");
            
            // Step 5: Calculate Total Savings
            BigDecimal totalDiscount = subtotal.subtract(finalPrice);
            breakdown.append("Total Discount: ").append(totalDiscount).append("\n");
            
            // Avoid division by zero
            if (subtotal.compareTo(BigDecimal.ZERO) > 0) {
                breakdown.append("Total Savings: ").append(totalDiscount.multiply(BigDecimal.valueOf(100))
                         .divide(subtotal, 2, RoundingMode.HALF_UP)).append("%\n");
            }
            
            // Check Margin Floor
            BigDecimal floorPriceBD = activePrice.getPriceFloor();
            BigDecimal finalUnitPrice = finalPrice.divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP);
            if (finalUnitPrice.compareTo(floorPriceBD) < 0) {
                breakdown.append("MARGIN VIOLATION ALERT\n");
                breakdown.append("Final unit price ").append(finalUnitPrice)
                    .append(" is below floor price ").append(floorPriceBD).append("\n\n");
                pricingCalculatorOutput.setText(breakdown.toString());
                int choice = JOptionPane.showConfirmDialog(this,
                    "Margin violation! Final unit price " + finalUnitPrice + " is below floor price " + floorPriceBD + ".\nWould you like to submit this override to manager workflows?",
                    "Margin Protection Alert", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (choice == JOptionPane.YES_OPTION) {
                    try {
                        String reqId = approvalEngine.submitOverrideRequest(DEFAULT_REQUESTED_BY,
                            ApprovalRequestType.MANUAL_DISCOUNT, 
                            buildPricingReference(skuId),
                            totalDiscount.doubleValue(), 
                            "Requested competitive matching override for customer " + customerId);
                        JOptionPane.showMessageDialog(this, "Approval request submitted!\nID: " + reqId, "Success", JOptionPane.INFORMATION_MESSAGE);
                        loadData();
                    } catch(Exception ex) {
                        JOptionPane.showMessageDialog(this, "Error submitting request: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
                return;
            }

            // Display in the output area
            pricingCalculatorOutput.setText(breakdown.toString());

            log("Price calculated for SKU: " + skuId + ", Customer: " + customerId + ", Final Price: " + finalPrice);
            
        } catch (Exception e) {
            log("ERROR calculating price: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Error calculating price: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void log(String message) {
        String logEntry = "[" + LocalTime.now() + "] " + message;
        SwingUtilities.invokeLater(() -> {
            if (logArea == null) {
                pendingLogEntries.add(logEntry);
                return;
            }
            flushPendingLogEntries();
            logArea.append(logEntry + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
    
    private JPanel createDynamicPricingPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        panel.add(createSectionTitleLabel("Dynamic Pricing Engine - Market-Based Price Adjustment"), BorderLayout.NORTH);
        
        JPanel inputPanel = new JPanel(new GridLayout(0, 2, 10, 10));
        inputPanel.setBorder(BorderFactory.createTitledBorder("Dynamic Price Calculation"));
        
        inputPanel.add(new JLabel("SKU ID:"));
        JTextField skuField = new JTextField(defaultSkuInput());
        inputPanel.add(skuField);
        
        inputPanel.add(new JLabel("Base Price ($):"));
        JTextField basePriceField = new JTextField("100.00");
        inputPanel.add(basePriceField);
        
        JButton calculateBtn = createButton("Calculate Dynamic Price", true);
        calculateBtn.addActionListener(e -> {
            try {
                String skuId = skuField.getText();
                double basePrice = Double.parseDouble(basePriceField.getText());
                
                double adjustedPrice = dynamicPricingEngine.adjustBasePrice(skuId, basePrice);
                String result = String.format(
                    "DYNAMIC PRICING RESULT\n" +
                    "======================\n\n" +
                    "SKU ID: %s\n" +
                    "Base Price: $%.2f\n" +
                    "Market Index: %.4f\n" +
                    "Adjusted Price: $%.2f\n" +
                    "Price Change: %.2f%%\n\n" +
                    "This price reflects current market conditions.\n" +
                    "Use this for competitive positioning.",
                    skuId, basePrice,
                    adjustedPrice / basePrice,
                    adjustedPrice,
                    ((adjustedPrice - basePrice) / basePrice) * 100
                );
                
                JOptionPane.showMessageDialog(this, result, "Dynamic Price Calculated", JOptionPane.INFORMATION_MESSAGE);
                log("Dynamic price calculated for " + skuId + ": $" + String.format("%.2f", adjustedPrice));
            } catch (Exception ex) {
                log("ERROR: " + ex.getMessage());
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        
        JButton tickBtn = createButton("Simulate Market Change", true);
        tickBtn.addActionListener(e -> {
            double newIndex = marketSimulator.tick();
            String result = String.format(
                "MARKET SIMULATION\n" +
                "=================\n\n" +
                "New Market Index: %.4f\n" +
                "Volatility: +/-2%% drift\n\n" +
                "Market conditions have changed!\n" +
                "Recalculate pricing with new index.",
                newIndex
            );
            JOptionPane.showMessageDialog(this, result, "Market Update", JOptionPane.INFORMATION_MESSAGE);
            log("Market simulated: New index = " + String.format("%.4f", newIndex));
        });
        
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        btnPanel.add(calculateBtn);
        btnPanel.add(tickBtn);
        
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.add(inputPanel, BorderLayout.CENTER);
        topPanel.add(btnPanel, BorderLayout.SOUTH);
        
        JTextArea infoArea = new JTextArea(
            "DYNAMIC PRICING ENGINE\n\n" +
            "This engine adjusts base prices based on real-time market conditions.\n\n" +
            "Features:\n" +
            "- Market index tracking\n" +
            "- Automated price adjustment\n" +
            "- Competitive positioning\n" +
            "- Real-time sensitivity\n\n" +
            "Use Cases:\n" +
            "- Inventory management\n" +
            "- Demand-driven pricing\n" +
            "- Competitive response\n" +
            "- Revenue optimization"
        );
        infoArea.setEditable(false);
        infoArea.setLineWrap(true);
        infoArea.setWrapStyleWord(true);
        infoArea.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        infoArea.setBackground(new Color(245, 250, 245));
        
        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(infoArea, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createCurrencyPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        panel.add(createSectionTitleLabel("Multi-Currency Exchange Simulator"), BorderLayout.NORTH);
        
        JPanel inputPanel = new JPanel(new GridLayout(0, 2, 10, 10));
        inputPanel.setBorder(BorderFactory.createTitledBorder("Currency Conversion"));
        
        inputPanel.add(new JLabel("Amount:"));
        JTextField amountField = new JTextField("1000.00");
        inputPanel.add(amountField);
        
        inputPanel.add(new JLabel("From Currency:"));
        JComboBox<String> fromCurrencyCombo = new JComboBox<>(new String[]{"INR", "USD", "EUR", "GBP"});
        fromCurrencyCombo.setSelectedItem("INR");
        inputPanel.add(fromCurrencyCombo);
        
        inputPanel.add(new JLabel("To Currency:"));
        JComboBox<String> toCurrencyCombo = new JComboBox<>(new String[]{"INR", "USD", "EUR", "GBP"});
        toCurrencyCombo.setSelectedItem("USD");
        inputPanel.add(toCurrencyCombo);
        
        JButton convertBtn = createButton("Convert Currency", true);
        convertBtn.addActionListener(e -> {
            try {
                double amount = Double.parseDouble(amountField.getText());
                String from = (String) fromCurrencyCombo.getSelectedItem();
                String to = (String) toCurrencyCombo.getSelectedItem();
                
                double converted = currencySimulator.convert(amount, from, to);
                double rate = currencySimulator.getRate(from, to);
                
                String result = String.format(
                    "CURRENCY CONVERSION\n" +
                    "===================\n\n" +
                    "Amount: %.2f %s\n" +
                    "Exchange Rate: 1 %s = %.4f %s\n" +
                    "Converted: %.2f %s\n\n" +
                    "International Pricing:\n" +
                    "Use this for global market pricing.",
                    amount, from, from, rate, to, converted, to
                );
                
                JOptionPane.showMessageDialog(this, result, "Conversion Result", JOptionPane.INFORMATION_MESSAGE);
                log("Converted " + amount + " " + from + " to " + to + " = " + String.format("%.2f", converted) + " " + to);
            } catch (Exception ex) {
                log("ERROR: " + ex.getMessage());
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        
        JButton nudgeBtn = createButton("Simulate Market Fluctuation", true);
        nudgeBtn.addActionListener(e -> {
            currencySimulator.nudgeRates();
            log("Market rates fluctuated (+/-0.5%)");
            String updatedRates = String.format(
                "UPDATED EXCHANGE RATES (to INR)\n" +
                "===============================\n\n" +
                "USD = %.2f (fluctuated +/-0.5%%)\n" +
                "EUR = %.2f (fluctuated +/-0.5%%)\n" +
                "GBP = %.2f (fluctuated +/-0.5%%)\n\n" +
                "Market has moved! Rates are live.\n" +
                "Re-convert to see new prices.",
                currencySimulator.getRate("USD", "INR"),
                currencySimulator.getRate("EUR", "INR"),
                currencySimulator.getRate("GBP", "INR")
            );
            JOptionPane.showMessageDialog(this, updatedRates, "Market Update", JOptionPane.INFORMATION_MESSAGE);
            rateArea.setText(
                "CURRENT EXCHANGE RATES (to INR)\n" +
                "===============================\n\n" +
                "INR = 1.00\n" +
                String.format("USD = %.2f (fluctuated +/-0.5%%)\n", currencySimulator.getRate("USD", "INR")) +
                String.format("EUR = %.2f (fluctuated +/-0.5%%)\n", currencySimulator.getRate("EUR", "INR")) +
                String.format("GBP = %.2f (fluctuated +/-0.5%%)\n\n", currencySimulator.getRate("GBP", "INR")) +
                "Multi-Currency Management:\n" +
                "- Support 4 major currencies\n" +
                "- Real-time rate simulation\n" +
                "- Market volatility modeling\n" +
                "- Automatic conversion\n\n" +
                "Global Strategy:\n" +
                "Price in local currencies for each region"
            );
        });
        
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        btnPanel.add(convertBtn);
        btnPanel.add(nudgeBtn);
        
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.add(inputPanel, BorderLayout.CENTER);
        topPanel.add(btnPanel, BorderLayout.SOUTH);
        
        rateArea = new JTextArea(
            "CURRENT EXCHANGE RATES (to INR)\n" +
            "===============================\n\n" +
            "INR = 1.00\n" +
            "USD = 83.00 (fluctuates +/-0.5%)\n" +
            "EUR = 90.00 (fluctuates +/-0.5%)\n" +
            "GBP = 105.00 (fluctuates +/-0.5%)\n\n" +
            "Multi-Currency Management:\n" +
            "- Support 4 major currencies\n" +
            "- Real-time rate simulation\n" +
            "- Market volatility modeling\n" +
            "- Automatic conversion\n\n" +
            "Global Strategy:\n" +
            "Price in local currencies for each region"
        );
        rateArea.setEditable(false);
        rateArea.setLineWrap(true);
        rateArea.setWrapStyleWord(true);
        rateArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        rateArea.setBackground(new Color(240, 245, 255));
        
        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(rateArea), BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createRegionalPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        panel.add(createSectionTitleLabel("Regional Pricing & Landed Cost Management"), BorderLayout.NORTH);
        
        JPanel inputPanel = new JPanel(new GridLayout(0, 2, 10, 10));
        inputPanel.setBorder(BorderFactory.createTitledBorder("Regional Price Adjustment"));
        
        inputPanel.add(new JLabel("SKU ID:"));
        JTextField skuField = new JTextField(defaultSkuInput());
        inputPanel.add(skuField);
        
        inputPanel.add(new JLabel("Base Price ($):"));
        JTextField basePriceField = new JTextField("100.00");
        inputPanel.add(basePriceField);
        
        inputPanel.add(new JLabel("Region:"));
        JComboBox<String> regionCombo = new JComboBox<>(new String[]{"GLOBAL", "SOUTH", "NORTH", "EU", "US"});
        inputPanel.add(regionCombo);
        
        JButton calculateBtn = createButton("Calculate Regional Price", true);
        calculateBtn.addActionListener(e -> {
            try {
                String skuId = skuField.getText();
                double basePrice = Double.parseDouble(basePriceField.getText());
                String region = (String) regionCombo.getSelectedItem();
                
                double landedCost = regionalPricingService.getLandedCost(skuId, region);
                double regionalPrice = regionalPricingService.applyRegionalPricingAdjustment(skuId, basePrice, region);
                
                String result = String.format(
                    "REGIONAL PRICING\n" +
                    "================\n\n" +
                    "SKU ID: %s\n" +
                    "Region: %s\n" +
                    "Base Price: $%.2f\n" +
                    "Landed Cost Multiplier: %.2f\n" +
                    "Regional Price: $%.2f\n" +
                    "Price Adjustment: %.2f%%\n\n" +
                    "Regional Factors:\n" +
                    "- Shipping costs\n" +
                    "- Local taxes\n" +
                    "- Customs duties\n" +
                    "- Regional demand",
                    skuId, region, basePrice, landedCost, regionalPrice,
                    ((regionalPrice - basePrice) / basePrice) * 100
                );
                
                JOptionPane.showMessageDialog(this, result, "Regional Price Calculated", JOptionPane.INFORMATION_MESSAGE);
                log("Regional price calculated for " + region + ": $" + String.format("%.2f", regionalPrice));
            } catch (Exception ex) {
                log("ERROR: " + ex.getMessage());
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        
        JPanel btnPanel = new JPanel();
        btnPanel.add(calculateBtn);
        
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.add(inputPanel, BorderLayout.CENTER);
        topPanel.add(btnPanel, BorderLayout.SOUTH);
        
        JTextArea regionInfoArea = new JTextArea(
            "REGIONAL MULTIPLIERS AND LANDED COST\n" +
            "====================================\n\n" +
            "GLOBAL:  1.00 (Base)\n" +
            "SOUTH:   1.02 (India region, minimal markup)\n" +
            "NORTH:   1.03 (Northern region premium)\n" +
            "EU:      1.10 (European import duties & taxes)\n" +
            "US:      1.08 (US distribution markup)\n\n" +
            "Landed Cost Components:\n" +
            "- FOB Price (base)\n" +
            "- International Freight\n" +
            "- Insurance\n" +
            "- Customs & Duties\n" +
            "- Local Distribution\n\n" +
            "Strategic Pricing:\n" +
            "- Account for regional costs\n" +
            "- Maintain profit margins\n" +
            "- Stay competitive locally\n" +
            "- Optimize supply chain"
        );
        regionInfoArea.setEditable(false);
        regionInfoArea.setLineWrap(true);
        regionInfoArea.setWrapStyleWord(true);
        regionInfoArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        regionInfoArea.setBackground(new Color(245, 255, 245));
        
        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(regionInfoArea, BorderLayout.CENTER);
        
        return panel;
    }
    
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to set look and feel", e);
        }
        
        SwingUtilities.invokeLater(() -> new PricingSubsystemGUI());
    }
}
