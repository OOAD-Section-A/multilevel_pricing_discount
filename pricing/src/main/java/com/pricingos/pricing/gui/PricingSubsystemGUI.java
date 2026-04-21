package com.pricingos.pricing.gui;

import com.jackfruit.scm.database.adapter.PricingAdapter;
import com.jackfruit.scm.database.facade.SupplyChainDatabaseFacade;
import com.jackfruit.scm.database.facade.subsystem.PricingSubsystemFacade;
import com.jackfruit.scm.database.model.PriceList;
import com.jackfruit.scm.database.model.PricingModels;
import com.scm.subsystems.MultiLevelPricingSubsystem;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PricingSubsystemGUI extends JFrame {
    
    private static final Logger LOGGER = Logger.getLogger(PricingSubsystemGUI.class.getName());

    private SupplyChainDatabaseFacade dbFacade;
    private PricingSubsystemFacade pricingFacade;
    private PricingAdapter pricingAdapter;
    private com.pricingos.pricing.promotion.PromotionManager promotionManager;
    private com.pricingos.pricing.pricelist.PriceListManager priceListManager;
    private com.pricingos.pricing.approval.ApprovalWorkflowEngine approvalEngine;
    private com.pricingos.pricing.approval.ProfitabilityAnalyticsObserver analyticsObserver;
    private com.pricingos.pricing.promotion.RebateProgramManager rebateProgramManager;
    private com.pricingos.pricing.simulation.DynamicPricingEngine dynamicPricingEngine;
    private com.pricingos.pricing.simulation.CurrencySimulator currencySimulator;
    private com.pricingos.pricing.simulation.RegionalPricingService regionalPricingService;
    private com.pricingos.pricing.simulation.MarketPriceSimulator marketSimulator;
    private MultiLevelPricingSubsystem exceptions;
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    
    private MultiLevelPricingSubsystem getExceptions() {
        if (exceptions == null && IS_WINDOWS) {
            try {
                exceptions = MultiLevelPricingSubsystem.INSTANCE;
                log("DEBUG: Exception handler initialized successfully");
                LOGGER.info("Exception handler initialized: " + exceptions.getClass().getName());
            } catch (Exception e) {
                // Windows Event Viewer initialization failed
                log("DEBUG: Exception handler initialization failed: " + e.getMessage());
                LOGGER.log(Level.SEVERE, "Failed to initialize exception handler", e);
                exceptions = null;
            }
        } else if (!IS_WINDOWS) {
            log("DEBUG: Not running on Windows - exception handler disabled");
        }
        return exceptions;
    }
    
    /**
     * Temporary workaround for logging unregistered exceptions (ID 0).
     * 
     * The raise(int id, String name, String message, Severity severity) method 
     * in MultiLevelPricingSubsystem is currently PRIVATE. This method logs the 
     * exception locally until exceptions team makes raise() public.
     * 
     * TODO: Replace with exceptions.raise(0, name, message, severity) after exceptions team update
     * 
     * @param exceptionId Exception type (ID 0 for unregistered)
     * @param exceptionName Name of the exception type
     * @param message Detailed error message
     */
    private void logUnregistegeredException(int exceptionId, String exceptionName, String message) {
        String logMessage = String.format(
            "[UNREGISTERED_EXCEPTION_ID_%d] %s: %s",
            exceptionId, exceptionName, message
        );
        LOGGER.warning(logMessage);
        log("WARNING: " + logMessage);
    }
    
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
        loadData();
    }

    private void initializeSubsystemAPIs() {
        try {
            com.pricingos.common.ISkuCatalogService skuCatalogService = new com.pricingos.common.ISkuCatalogService() {
                @Override public boolean isSkuActive(String skuId) { return true; }
                @Override public java.util.List<String> getAllActiveSkuIds() {
                    return java.util.List.of("SKU-APPLE-001", "SKU-BANANA-001", "SKU-ORANGE-001");
                }
            };
            promotionManager = new com.pricingos.pricing.promotion.PromotionManager(skuCatalogService, pricingAdapter);
            priceListManager = new com.pricingos.pricing.pricelist.PriceListManager();
            rebateProgramManager = new com.pricingos.pricing.promotion.RebateProgramManager(pricingAdapter);
            
            marketSimulator = new com.pricingos.pricing.simulation.MarketPriceSimulator();
            dynamicPricingEngine = new com.pricingos.pricing.simulation.DynamicPricingEngine(marketSimulator);
            currencySimulator = new com.pricingos.pricing.simulation.CurrencySimulator();
            regionalPricingService = new com.pricingos.pricing.simulation.RegionalPricingService(pricingAdapter);

            com.pricingos.pricing.approval.ApprovalRoutingStrategy strategy = new com.pricingos.pricing.approval.ApprovalRoutingStrategy() {
                @Override public String resolveApproverId(com.pricingos.pricing.approval.ApprovalRequest request) { return "MANAGER_123"; }
                @Override public boolean requiresDualApproval(com.pricingos.pricing.approval.ApprovalRequest request) { return false; }
            };
            com.pricingos.common.IApproverRoleService roleService = new com.pricingos.common.IApproverRoleService() {
                @Override public boolean canApprove(String approverId, com.pricingos.common.ApprovalRequestType type, double discount) { return true; }
                @Override public String getEscalationManagerId(String currentApprover) { return "DIRECTOR_123"; }
            };
            approvalEngine = new com.pricingos.pricing.approval.ApprovalWorkflowEngine(strategy, roleService);
            
            com.pricingos.common.IFloorPriceService floorPriceService = new com.pricingos.common.IFloorPriceService() {
                @Override public boolean wouldViolateMargin(String orderId, double price) { return false; } // Explcitly checked in GUI
                @Override public double getEffectiveFloorPrice(String orderId) { return 100.0; }
            };
            approvalEngine.withFloorPriceService(floorPriceService);
            
            analyticsObserver = new com.pricingos.pricing.approval.ProfitabilityAnalyticsObserver();
            approvalEngine.addObserver(analyticsObserver);

            log("Initialized subsystem APIs: PromotionManager, RebateProgramManager, PriceListManager, ApprovalWorkflowEngine, AnalyticsObserver");
        } catch (Exception e) {
            log("ERROR initializing subsystem APIs: " + e.getMessage());
            LOGGER.log(Level.SEVERE, "Failed to initialize subsystem APIs", e);
        }
    }
    
    private void initializeDatabaseConnection() {
        try {
            log("Connecting to database OOAD...");
            dbFacade = new SupplyChainDatabaseFacade();
            pricingFacade = dbFacade.pricing();
            pricingAdapter = new PricingAdapter(dbFacade);
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
        
        JButton refreshButton = new JButton("Refresh All Data");
        refreshButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        refreshButton.setBackground(Color.WHITE);
        refreshButton.addActionListener(e -> {
            loadData();
            log("Data refreshed from database");
        });
        
        panel.add(refreshButton, BorderLayout.EAST);
        
        return panel;
    }
    
    private JPanel createPriceListPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        // Title
        JLabel titleLabel = new JLabel("Price List Management");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        panel.add(titleLabel, BorderLayout.NORTH);
        
        // Create table
        String[] columnNames = {"Price ID", "SKU ID", "Region", "Channel", "Price Type", 
                               "Base Price", "Floor Price", "Currency", "Status"};
        priceTableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        priceListTable = new JTable(priceTableModel);
        priceListTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        priceListTable.setRowHeight(28);
        priceListTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        priceListTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        priceListTable.setAutoCreateRowSorter(true);
        
        JScrollPane scrollPane = new JScrollPane(priceListTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));

        JButton addPriceButton = new JButton("Add Price");
        addPriceButton.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        addPriceButton.addActionListener(e -> showAddPriceDialog());

        JButton deletePriceButton = new JButton("Delete Price");
        deletePriceButton.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        deletePriceButton.addActionListener(e -> deleteSelectedPrice());

        JButton viewDetailsButton = new JButton("View Details");
        viewDetailsButton.setFont(new Font("Segoe UI", Font.PLAIN, 12));
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
        
        JLabel titleLabel = new JLabel("Customer Tier Definitions");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        panel.add(titleLabel, BorderLayout.NORTH);
        
        String[] columnNames = {"Tier ID", "Tier Name", "Min Spend Threshold", "Default Discount %"};
        tierTableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        tierTable = new JTable(tierTableModel);
        tierTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        tierTable.setRowHeight(28);
        tierTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        tierTable.setAutoCreateRowSorter(true);
        
        JScrollPane scrollPane = new JScrollPane(tierTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        panel.add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));

        JButton addTierButton = new JButton("Create Tier");
        addTierButton.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        addTierButton.addActionListener(e -> showAddTierDialog());

        buttonPanel.add(addTierButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }
    
    private JPanel createPromotionsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        JLabel titleLabel = new JLabel("Promotional & Seasonal Rule Engine");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        panel.add(titleLabel, BorderLayout.NORTH);
        
        String[] columnNames = {"Promo ID", "Promo Name", "Coupon Code", "Discount Type", 
                               "Discount Value", "Start Date", "End Date", "Max Uses", "Current Uses"};
        promotionsTableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        promotionsTable = new JTable(promotionsTableModel);
        promotionsTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        promotionsTable.setRowHeight(28);
        promotionsTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        promotionsTable.setAutoCreateRowSorter(true);
        
        JScrollPane scrollPane = new JScrollPane(promotionsTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }
    
    private JPanel createPromoCodeManagerPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel titleLabel = new JLabel("Promo Code Manager (Using PromotionManager API)");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        panel.add(titleLabel, BorderLayout.NORTH);

        // Create Promo Section
        JPanel createPanel = new JPanel(new GridLayout(0, 2, 10, 10));
        createPanel.setBorder(BorderFactory.createTitledBorder("Create New Promotion"));

        createPanel.add(new JLabel("Promotion Name:"));
        JTextField nameField = new JTextField("Summer Sale 2024");
        createPanel.add(nameField);

        createPanel.add(new JLabel("Coupon Code:"));
        JTextField codeField = new JTextField("SUMMER24");
        createPanel.add(codeField);

        createPanel.add(new JLabel("Discount Type:"));
        JComboBox<String> typeCombo = new JComboBox<>(new String[]{"PERCENTAGE_OFF", "FIXED_AMOUNT", "BUY_X_GET_Y"});
        createPanel.add(typeCombo);

        createPanel.add(new JLabel("Discount Value:"));
        JTextField valueField = new JTextField("15.00");
        createPanel.add(valueField);

        createPanel.add(new JLabel("Eligible SKUs (comma-separated):"));
        JTextField skuField = new JTextField("SKU-APPLE-001,SKU-BANANA-001");
        createPanel.add(skuField);

        createPanel.add(new JLabel("Min Cart Value:"));
        JTextField minCartField = new JTextField("0.00");
        createPanel.add(minCartField);

        createPanel.add(new JLabel("Max Uses (0 for unlimited):"));
        JTextField maxUsesField = new JTextField("100");
        createPanel.add(maxUsesField);

        createPanel.add(new JLabel("Start Date (YYYY-MM-DD):"));
        java.time.LocalDate today = java.time.LocalDate.now();
        JTextField startDateField = new JTextField(today.toString());
        createPanel.add(startDateField);

        createPanel.add(new JLabel("End Date (YYYY-MM-DD):"));
        java.time.LocalDate futureDate = today.plusMonths(6);
        JTextField endDateField = new JTextField(futureDate.toString());
        createPanel.add(endDateField);

        JButton createButton = new JButton("Create Promotion");
        createButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        createButton.addActionListener(e -> {
            try {
                String name = nameField.getText();
                String couponCode = codeField.getText();
                String discountType = (String) typeCombo.getSelectedItem();
                double discountValue;
                List<String> eligibleSkus;
                double minCartValue;
                int maxUses;
                java.time.LocalDate startDate;
                java.time.LocalDate endDate;

                try {
                    discountValue = Double.parseDouble(valueField.getText());
                    minCartValue = Double.parseDouble(minCartField.getText());
                    maxUses = Integer.parseInt(maxUsesField.getText());
                } catch (NumberFormatException nfe) {
                    // TEMPORARY WORKAROUND: Using local logging instead of exceptions.raise(0, ...)
                    // because raise() method is PRIVATE in MultiLevelPricingSubsystem
                    // TODO: Change to exceptions.raise(0, "INVALID_NUMBER_INPUT", ...) after exceptions team makes raise() public
                    logUnregistegeredException(0, "INVALID_NUMBER_INPUT", 
                        "Promotion creation input validation failed: " + nfe.getMessage());
                    log("ERROR: Invalid numeric input for promotion creation");
                    return;
                }

                eligibleSkus = List.of(skuField.getText().split(","));
                startDate = java.time.LocalDate.parse(startDateField.getText());
                endDate = java.time.LocalDate.parse(endDateField.getText());

                log("Creating promotion: " + name + " with code: " + couponCode + " for SKUs: " + eligibleSkus);
                log("Dates: " + startDate + " to " + endDate + ", Min cart: " + minCartValue);

                // Use PromotionManager API
                com.pricingos.common.DiscountType type = com.pricingos.common.DiscountType.valueOf(discountType);
                String promoId = promotionManager.createPromotion(name, couponCode, type, discountValue,
                    startDate, endDate, eligibleSkus, minCartValue, maxUses);

                log("Created promotion via PromotionManager: " + promoId + " - " + name);
                JOptionPane.showMessageDialog(this, "Promotion created successfully via PromotionManager API!\nID: " + promoId + "\nCoupon Code: " + couponCode,
                    "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                log("ERROR creating promotion: " + ex.getMessage());
                ex.printStackTrace();
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
        JTextField validateCodeField = new JTextField("SUMMER24");
        validatePanel.add(validateCodeField);

        validatePanel.add(new JLabel("SKU ID:"));
        JTextField validateSkuField = new JTextField("SKU-APPLE-001");
        validatePanel.add(validateSkuField);

        validatePanel.add(new JLabel("Cart Total:"));
        JTextField cartTotalField = new JTextField("100.00");
        validatePanel.add(cartTotalField);

        JButton validateButton = new JButton("Validate & Get Discount");
        validateButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
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
            } catch (com.pricingos.pricing.promotion.InvalidPromoCodeException ex) {
                try {
                    if (getExceptions() != null) {
                        exceptions.onInvalidPromoCode(couponCode);
                    }
                } catch (Exception ehEx) {
                    log("Exception handler error: " + ehEx.getMessage());
                }
                // Exception popup already shown by handler; no additional GUI message needed
                return;
            } catch (Exception ex) {
                log("ERROR validating promo code: " + ex.getMessage());
                ex.printStackTrace();
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

        JButton refreshButton = new JButton("Refresh Active Codes");
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

        JLabel titleLabel = new JLabel("Rebate Program Manager (Volume-Based Rebates)");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        panel.add(titleLabel, BorderLayout.NORTH);

        // Create Rebate Section
        JPanel createPanel = new JPanel(new GridLayout(0, 2, 10, 10));
        createPanel.setBorder(BorderFactory.createTitledBorder("Create New Rebate Program"));

        createPanel.add(new JLabel("Customer ID:"));
        JTextField customerIdField = new JTextField("CUST-12345");
        createPanel.add(customerIdField);

        createPanel.add(new JLabel("SKU ID:"));
        JTextField skuIdField = new JTextField("SKU-APPLE-001");
        createPanel.add(skuIdField);

        createPanel.add(new JLabel("Target Spend ($):"));
        JTextField targetSpendField = new JTextField("5000.00");
        createPanel.add(targetSpendField);

        createPanel.add(new JLabel("Rebate Percent (%):"));
        JTextField rebatePercentField = new JTextField("5.0");
        createPanel.add(rebatePercentField);

        JButton createButton = new JButton("Create Rebate Program");
        createButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
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
        JTextField programIdField = new JTextField("RBT-1");
        purchasePanel.add(programIdField);

        purchasePanel.add(new JLabel("Purchase Amount ($):"));
        JTextField purchaseAmountField = new JTextField("1200.00");
        purchasePanel.add(purchaseAmountField);

        JButton recordButton = new JButton("Record Purchase");
        recordButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
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

        JButton refreshButton = new JButton("Refresh Programs");
        rebateFilterCustomerField = new JTextField(15);
        rebateFilterCustomerField.setToolTipText("Enter Customer ID to filter (e.g. CUST-12345)");
        
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
            display.append("╔════════════════════════════════════════════════════════════════════════╗\n");
            display.append("║                    ACTIVE REBATE PROGRAMS                             ║\n");
            display.append("╚════════════════════════════════════════════════════════════════════════╝\n\n");
            
            java.util.List<PricingModels.RebateProgram> programs = java.util.Collections.emptyList();
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
                display.append("─".repeat(82)).append("\n");
                
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
                    String status = targetMet ? "✓ EARNED" : "PENDING";
                    
                    String progressStr = String.format("%.0f/%.0f (%.1f%%)", 
                        accumulatedSpend, targetSpend, progressPct);
                    
                    display.append(String.format("%-12s %-15s %-15s %-15s $%-11.2f %s\n",
                        programId, customerId, skuId, progressStr, rebateDue, status));
                    
                    display.append(String.format("  Target Met: %s | Rebate Rate: %.1f%% | Rebate Due: $%.2f\n\n",
                        targetMet ? "YES" : "NO", rebatePct, rebateDue));
                }
            }
            
            display.append("\n═══════════════════════════════════════════════════════════════════════════\n");
            
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
        
        JLabel titleLabel = new JLabel("Pricing Calculator");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        panel.add(titleLabel, BorderLayout.NORTH);
        
        JPanel inputPanel = new JPanel(new GridLayout(0, 2, 10, 10));
        inputPanel.setBorder(BorderFactory.createTitledBorder("Input"));
        
        inputPanel.add(new JLabel("SKU:"));
        JTextField skuField = new JTextField("SKU-APPLE-001");
        inputPanel.add(skuField);
        
        inputPanel.add(new JLabel("Customer ID:"));
        JTextField customerField = new JTextField("CUST-12345");
        inputPanel.add(customerField);
        
        inputPanel.add(new JLabel("Quantity:"));
        JTextField quantityField = new JTextField("10");
        inputPanel.add(quantityField);
        
        inputPanel.add(new JLabel("Promo Code:"));
        JTextField promoField = new JTextField("SUMMER24");
        inputPanel.add(promoField);
        
        // Add dropdown to load rebate programs
        inputPanel.add(new JLabel("Load Rebate Program:"));
        JComboBox<String> rebateCombo = new JComboBox<>();
        rebateCombo.addItem("-- Select to Auto-Fill --");
        inputPanel.add(rebateCombo);
        
        JButton calculateButton = new JButton("Calculate Price");
        calculateButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        calculateButton.addActionListener(e -> calculatePrice(skuField, customerField, quantityField, promoField));
        
        JButton refreshRebatesBtn = new JButton("Refresh Rebate List");
        refreshRebatesBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        refreshRebatesBtn.addActionListener(e -> {
            String customerId = customerField.getText();
            rebateCombo.removeAllItems();
            rebateCombo.addItem("-- Select to Auto-Fill --");
            if (pricingAdapter != null && customerId != null && !customerId.trim().isEmpty()) {
                java.util.List<PricingModels.RebateProgram> programs = pricingAdapter.listRebateProgramsByCustomer(customerId.trim());
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
            
            List<PriceList> prices = pricingFacade.listPrices();
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
            
            log("Loaded " + prices.size() + " price records");
        } catch (Exception e) {
            log("ERROR loading price list: " + e.getMessage());
            LOGGER.log(Level.SEVERE, "Failed to load price list", e);
        }
    }
    
    private void loadTierDefinitions() {
        try {
            log("Loading tier definitions from database...");
            tierTableModel.setRowCount(0);
            
            List<PricingModels.TierDefinition> tiers = pricingFacade.listTierDefinitions();
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

            List<PricingModels.Promotion> promotions = pricingFacade.listPromotions();
            for (PricingModels.Promotion promo : promotions) {
                Object[] row = {
                    promo.promoId(),
                    promo.promoName(),
                    promo.couponCode(),
                    promo.discountType(),
                    promo.discountValue(),
                    promo.startDate(),
                    promo.endDate(),
                    promo.minCartValue(),
                    promo.maxUses()
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
        JTextField skuField = new JTextField("SKU-NEW-001");
        panel.add(skuField);
        
        panel.add(new JLabel("Region:"));
        JTextField regionField = new JTextField("SOUTH");
        panel.add(regionField);
        
        panel.add(new JLabel("Channel:"));
        JTextField channelField = new JTextField("RETAIL");
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
        
        JButton addButton = new JButton("Add Price");
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
                
                pricingFacade.publishPrice(newPrice);
                
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
        int selectedRow = priceListTable.getSelectedRow();
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
        int selectedRow = priceListTable.getSelectedRow();
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
        JTextField nameField = new JTextField("Platinum");
        panel.add(nameField);

        panel.add(new JLabel("Min Spend Threshold:"));
        JTextField thresholdField = new JTextField("100000.00");
        panel.add(thresholdField);

        panel.add(new JLabel("Default Discount %:"));
        JTextField discountField = new JTextField("20.00");
        panel.add(discountField);

        JButton addButton = new JButton("Create Tier");
        addButton.addActionListener(e -> {
            try {
                Integer tierId = (tierTableModel.getRowCount() + 1);
                String tierName = nameField.getText();
                BigDecimal minSpendThreshold = new BigDecimal(thresholdField.getText());
                BigDecimal defaultDiscountPct = new BigDecimal(discountField.getText());

                PricingModels.TierDefinition tier = new PricingModels.TierDefinition(
                    tierId, tierName, minSpendThreshold, defaultDiscountPct
                );

                pricingFacade.createTierDefinition(tier);

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
            log("Loading approval requests...");
            approvalTableModel.setRowCount(0);
            // Note: Approval requests are now maintained in-memory by ApprovalWorkflowEngine.
            // Database team's PricingAdapter does not expose an API for querying approval requests.
            // For now, showing empty table. In a full implementation, approval engine would provide a query method.
            log("Approval requests are maintained in-memory by the workflow engine");
        } catch (Exception e) { log("ERROR loading approvals: " + e.getMessage()); }
    }

    private void loadAnalytics() {
        try {
            log("Loading profitability analytics from database...");
            analyticsTableModel.setRowCount(0);
            for (com.pricingos.pricing.approval.ProfitabilityAnalyticsObserver.ProfitabilityEntry e : analyticsObserver.getAllRecords()) {
                analyticsTableModel.addRow(new Object[]{ e.approvalId(), e.requestType().name(), e.finalStatus().name(), e.discountAmount(), e.recordedAt() });
            }
            totalRevenueDeltaLabel.setText("Total Revenue Impact (Approved Discounts): " + analyticsObserver.getApprovedRevenueDelta());
        } catch (Exception e) { log("ERROR loading analytics: " + e.getMessage()); }
    }

    private JPanel createApprovalPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        JLabel titleLabel = new JLabel("Pending Approval Requests - Workflow Management");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        panel.add(titleLabel, BorderLayout.NORTH);
        
        String[] columns = {"ID", "Order", "Type", "Requested By", "Discount", "Justification", "Assigned To", "Submission Time", "Status"};
        approvalTableModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        approvalTable = new JTable(approvalTableModel);
        approvalTable.setRowHeight(24);
        
        // Double click to view details
        approvalTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    int row = approvalTable.getSelectedRow();
                    if (row >= 0) {
                        String id = (String) approvalTableModel.getValueAt(row, 0);
                        showApprovalDetails(id);
                    }
                }
            }
        });
        
        panel.add(new JScrollPane(approvalTable), BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        
        JButton detailsBtn = new JButton("📋 View Details");
        detailsBtn.addActionListener(e -> {
            int row = approvalTable.getSelectedRow();
            if (row >= 0) {
                String id = (String) approvalTableModel.getValueAt(row, 0);
                showApprovalDetails(id);
            } else { JOptionPane.showMessageDialog(panel, "Select a request first", "No Selection", JOptionPane.WARNING_MESSAGE); }
        });
        
        JButton approveBtn = new JButton("✓ Approve Request");
        approveBtn.addActionListener(e -> {
            int row = approvalTable.getSelectedRow();
            if (row >= 0) {
                String id = (String) approvalTableModel.getValueAt(row, 0);
                try {
                	approvalEngine.approve(id, "MANAGER_123");
                	loadData();
                	JOptionPane.showMessageDialog(panel, "Request Approved Successfully!");
                } catch(Exception ex) { JOptionPane.showMessageDialog(panel, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE); }
            } else { JOptionPane.showMessageDialog(panel, "Select a request first", "No Selection", JOptionPane.WARNING_MESSAGE); }
        });
        
        JButton rejectBtn = new JButton("✗ Reject Request");
        rejectBtn.addActionListener(e -> {
            int row = approvalTable.getSelectedRow();
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
                reasonArea.setText("Manual review rejection");
                
                JButton submitBtn = new JButton("Reject with Reason");
                submitBtn.addActionListener(ae -> {
                    try {
                        String reason = reasonArea.getText().trim();
                        if (reason.isEmpty()) reason = "Manual review rejection";
                        approvalEngine.reject(id, "MANAGER_123", reason);
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
        
        JButton escalateBtn = new JButton("⬆ Escalate Request");
        escalateBtn.addActionListener(e -> {
            int row = approvalTable.getSelectedRow();
            if (row >= 0) {
                String id = (String) approvalTableModel.getValueAt(row, 0);
                try {
                    approvalEngine.escalateStaleRequests();
                    loadData();
                    JOptionPane.showMessageDialog(panel, "Request escalated to higher authority!");
                } catch(Exception ex) { 
                    JOptionPane.showMessageDialog(panel, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE); 
                }
            } else { JOptionPane.showMessageDialog(panel, "Select a request first", "No Selection", JOptionPane.WARNING_MESSAGE); }
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
            
            com.pricingos.pricing.approval.ApprovalRequest request = approvalEngine.getRequestById(approvalId);
            
            StringBuilder details = new StringBuilder();
            details.append("═══ APPROVAL REQUEST DETAILS ═══\n\n");
            details.append("Request ID: ").append(request.getApprovalId()).append("\n");
            details.append("Order ID: ").append(request.getOrderId()).append("\n");
            details.append("Request Type: ").append(request.getRequestType().name()).append("\n");
            details.append("Status: ").append(request.getStatus().name()).append("\n\n");
            
            details.append("Requested By: ").append(request.getRequestedBy()).append("\n");
            details.append("Submission Time: ").append(request.getSubmissionTime()).append("\n");
            details.append("Routed To: ").append(request.getRoutedToApproverId()).append("\n\n");
            
            details.append("Requested Discount Amount: $").append(String.format("%.2f", request.getRequestedDiscountAmt())).append("\n\n");
            
            details.append("───── JUSTIFICATION ─────\n");
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
            
            details.append("\n═══════════════════════════════════\n");
            details.append("Note: Audit logs are now available in the application logger.\n");
            details.append("(Database persistence not available from database team)\n");
            
            JTextArea textArea = new JTextArea(details.toString());
            textArea.setEditable(false);
            textArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
            textArea.setBackground(new Color(240, 245, 250));
            
            JButton closeBtn = new JButton("Close");
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
        JLabel titleLabel = new JLabel("Profitability Analytics (Decisions Ledger)");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        header.add(titleLabel, BorderLayout.NORTH);
        totalRevenueDeltaLabel = new JLabel("Total Revenue Impact: 0.0");
        totalRevenueDeltaLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        header.add(totalRevenueDeltaLabel, BorderLayout.SOUTH);
        panel.add(header, BorderLayout.NORTH);
        
        String[] columns = {"Approval ID", "Request Type", "Final Status", "Discount Amount", "Recorded At"};
        analyticsTableModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        analyticsTable = new JTable(analyticsTableModel);
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
            
            StringBuilder breakdown = new StringBuilder();
            breakdown.append("=== PRICING CALCULATION ===\n\n");
            
            // Step 1: Fetch base price from database
            List<PriceList> prices = pricingFacade.listPrices();
            BigDecimal basePrice = null;
            for (PriceList price : prices) {
                if (price.getSkuId().equals(skuId) && "ACTIVE".equals(price.getStatus())) {
                    basePrice = price.getBasePrice();
                    breakdown.append("Base Price: ").append(basePrice).append(" ").append(price.getCurrencyCode()).append("\n");
                    break;
                }
            }
            
            if (basePrice == null) {
                basePrice = new BigDecimal("100.00"); // fallback
                breakdown.append("Base Price: ").append(basePrice).append(" (default)\n");
            }
            
            BigDecimal subtotal = basePrice.multiply(BigDecimal.valueOf(quantity));
            breakdown.append("Subtotal (").append(quantity).append(" units): ").append(subtotal).append("\n\n");
            
            // Step 2: Apply Tier Discount
            List<PricingModels.TierDefinition> tiers = pricingFacade.listTierDefinitions();
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
            } catch (com.pricingos.pricing.promotion.InvalidPromoCodeException ex) {
                breakdown.append("Promo Code Invalid: ").append(ex.getReason()).append("\n");
                promoDiscount = BigDecimal.ZERO;
            }

            BigDecimal afterPromo = afterTier.subtract(promoDiscount);
            breakdown.append("Promo Discount: -").append(promoDiscount).append("\n");
            breakdown.append("After Promo: ").append(afterPromo).append("\n\n");
            
            // Step 3B: Check Rebate Eligibility
            breakdown.append("=== REBATE PROGRAM STATUS ===\n");
            
            java.util.List<PricingModels.RebateProgram> rebatePrograms = java.util.Collections.emptyList();
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
                            breakdown.append("  Status: ✓ TARGET MET - Rebate Due: $").append(String.format("%.2f", rebateDue)).append("\n\n");
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
                         .divide(subtotal, 2, java.math.RoundingMode.HALF_UP)).append("%\n");
            }
            
            // Check Margin Floor
            BigDecimal floorPriceBD = new BigDecimal("100.00");
            for (PriceList price : prices) {
                if (price.getSkuId().equals(skuId) && "ACTIVE".equals(price.getStatus())) {
                    floorPriceBD = price.getPriceFloor();
                    break;
                }
            }
            if (finalPrice.compareTo(floorPriceBD) < 0) {
                breakdown.append("⚠️ MARGIN VIOLATION ALERT ⚠️\nFinal Price is below Floor Price of ").append(floorPriceBD).append("\n\n");
                pricingCalculatorOutput.setText(breakdown.toString());
                int choice = JOptionPane.showConfirmDialog(this,
                    "Margin violation! Final price " + finalPrice + " is below floor price " + floorPriceBD + ".\nWould you like to submit this override to manager workflows?",
                    "Margin Protection Alert", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (choice == JOptionPane.YES_OPTION) {
                    try {
                        String reqId = approvalEngine.submitOverrideRequest("SalesAgent-01", 
                            com.pricingos.common.ApprovalRequestType.MANUAL_DISCOUNT, 
                            "ORDER-" + System.currentTimeMillis(), 
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
        String logMessage = "[" + java.time.LocalTime.now() + "] " + message;
        LOGGER.info(logMessage);
        
        if (logArea != null) {
            SwingUtilities.invokeLater(() -> {
                if (logArea != null) {
                    logArea.append(logMessage + "\n");
                    logArea.setCaretPosition(logArea.getDocument().getLength());
                }
            });
        }
    }
    
    private JPanel createDynamicPricingPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        JLabel titleLabel = new JLabel("Dynamic Pricing Engine - Market-Based Price Adjustment");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        panel.add(titleLabel, BorderLayout.NORTH);
        
        JPanel inputPanel = new JPanel(new GridLayout(0, 2, 10, 10));
        inputPanel.setBorder(BorderFactory.createTitledBorder("Dynamic Price Calculation"));
        
        inputPanel.add(new JLabel("SKU ID:"));
        JTextField skuField = new JTextField("SKU-APPLE-001");
        inputPanel.add(skuField);
        
        inputPanel.add(new JLabel("Base Price ($):"));
        JTextField basePriceField = new JTextField("100.00");
        inputPanel.add(basePriceField);
        
        JButton calculateBtn = new JButton("Calculate Dynamic Price");
        calculateBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        calculateBtn.addActionListener(e -> {
            try {
                String skuId = skuField.getText();
                double basePrice = Double.parseDouble(basePriceField.getText());
                
                double adjustedPrice = dynamicPricingEngine.adjustBasePrice(skuId, basePrice);
                String result = String.format(
                    "═══ DYNAMIC PRICING RESULT ═══\n\n" +
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
        
        JButton tickBtn = new JButton("Simulate Market Change");
        tickBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        tickBtn.addActionListener(e -> {
            double newIndex = marketSimulator.tick();
            String result = String.format(
                "═══ MARKET SIMULATION ═══\n\n" +
                "New Market Index: %.4f\n" +
                "Volatility: ±2%% drift\n\n" +
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
            "• Market index tracking\n" +
            "• Automated price adjustment\n" +
            "• Competitive positioning\n" +
            "• Real-time sensitivity\n\n" +
            "Use Cases:\n" +
            "• Inventory management\n" +
            "• Demand-driven pricing\n" +
            "• Competitive response\n" +
            "• Revenue optimization"
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
        
        JLabel titleLabel = new JLabel("Multi-Currency Exchange Simulator");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        panel.add(titleLabel, BorderLayout.NORTH);
        
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
        
        JButton convertBtn = new JButton("Convert Currency");
        convertBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        convertBtn.addActionListener(e -> {
            try {
                double amount = Double.parseDouble(amountField.getText());
                String from = (String) fromCurrencyCombo.getSelectedItem();
                String to = (String) toCurrencyCombo.getSelectedItem();
                
                double converted = currencySimulator.convert(amount, from, to);
                double rate = currencySimulator.getRate(from, to);
                
                String result = String.format(
                    "═══ CURRENCY CONVERSION ═══\n\n" +
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
        
        JButton nudgeBtn = new JButton("Simulate Market Fluctuation");
        nudgeBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        nudgeBtn.addActionListener(e -> {
            currencySimulator.nudgeRates();
            log("Market rates fluctuated (±0.5%)");
            String updatedRates = String.format(
                "═══ UPDATED EXCHANGE RATES (to INR) ═══\n\n" +
                "USD = %.2f (fluctuated ±0.5%%)\n" +
                "EUR = %.2f (fluctuated ±0.5%%)\n" +
                "GBP = %.2f (fluctuated ±0.5%%)\n\n" +
                "Market has moved! Rates are live.\n" +
                "Re-convert to see new prices.",
                currencySimulator.getRate("USD", "INR"),
                currencySimulator.getRate("EUR", "INR"),
                currencySimulator.getRate("GBP", "INR")
            );
            JOptionPane.showMessageDialog(this, updatedRates, "Market Update", JOptionPane.INFORMATION_MESSAGE);
            rateArea.setText(
                "═══ CURRENT EXCHANGE RATES (to INR) ═══\n\n" +
                "INR = 1.00\n" +
                String.format("USD = %.2f (fluctuated ±0.5%%)\n", currencySimulator.getRate("USD", "INR")) +
                String.format("EUR = %.2f (fluctuated ±0.5%%)\n", currencySimulator.getRate("EUR", "INR")) +
                String.format("GBP = %.2f (fluctuated ±0.5%%)\n\n", currencySimulator.getRate("GBP", "INR")) +
                "Multi-Currency Management:\n" +
                "• Support 4 major currencies\n" +
                "• Real-time rate simulation\n" +
                "• Market volatility modeling\n" +
                "• Automatic conversion\n\n" +
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
            "═══ CURRENT EXCHANGE RATES (to INR) ═══\n\n" +
            "INR = 1.00\n" +
            "USD = 83.00 (fluctuates ±0.5%)\n" +
            "EUR = 90.00 (fluctuates ±0.5%)\n" +
            "GBP = 105.00 (fluctuates ±0.5%)\n\n" +
            "Multi-Currency Management:\n" +
            "• Support 4 major currencies\n" +
            "• Real-time rate simulation\n" +
            "• Market volatility modeling\n" +
            "• Automatic conversion\n\n" +
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
        
        JLabel titleLabel = new JLabel("Regional Pricing & Landed Cost Management");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        panel.add(titleLabel, BorderLayout.NORTH);
        
        JPanel inputPanel = new JPanel(new GridLayout(0, 2, 10, 10));
        inputPanel.setBorder(BorderFactory.createTitledBorder("Regional Price Adjustment"));
        
        inputPanel.add(new JLabel("SKU ID:"));
        JTextField skuField = new JTextField("SKU-APPLE-001");
        inputPanel.add(skuField);
        
        inputPanel.add(new JLabel("Base Price ($):"));
        JTextField basePriceField = new JTextField("100.00");
        inputPanel.add(basePriceField);
        
        inputPanel.add(new JLabel("Region:"));
        JComboBox<String> regionCombo = new JComboBox<>(new String[]{"GLOBAL", "SOUTH", "NORTH", "EU", "US"});
        inputPanel.add(regionCombo);
        
        JButton calculateBtn = new JButton("Calculate Regional Price");
        calculateBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        calculateBtn.addActionListener(e -> {
            try {
                String skuId = skuField.getText();
                double basePrice = Double.parseDouble(basePriceField.getText());
                String region = (String) regionCombo.getSelectedItem();
                
                double landedCost = regionalPricingService.getLandedCost(skuId, region);
                double regionalPrice = regionalPricingService.applyRegionalPricingAdjustment(skuId, basePrice, region);
                
                String result = String.format(
                    "═══ REGIONAL PRICING ═══\n\n" +
                    "SKU ID: %s\n" +
                    "Region: %s\n" +
                    "Base Price: $%.2f\n" +
                    "Landed Cost Multiplier: %.2f\n" +
                    "Regional Price: $%.2f\n" +
                    "Price Adjustment: %.2f%%\n\n" +
                    "Regional Factors:\n" +
                    "• Shipping costs\n" +
                    "• Local taxes\n" +
                    "• Customs duties\n" +
                    "• Regional demand",
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
            "═════ REGIONAL MULTIPLIERS & LANDED COST ═════\n\n" +
            "GLOBAL:  1.00 (Base)\n" +
            "SOUTH:   1.02 (India region, minimal markup)\n" +
            "NORTH:   1.03 (Northern region premium)\n" +
            "EU:      1.10 (European import duties & taxes)\n" +
            "US:      1.08 (US distribution markup)\n\n" +
            "Landed Cost Components:\n" +
            "• FOB Price (base)\n" +
            "• International Freight\n" +
            "• Insurance\n" +
            "• Customs & Duties\n" +
            "• Local Distribution\n\n" +
            "Strategic Pricing:\n" +
            "• Account for regional costs\n" +
            "• Maintain profit margins\n" +
            "• Stay competitive locally\n" +
            "• Optimize supply chain"
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
