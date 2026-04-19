package com.pricingos.pricing.gui;

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
    private com.pricingos.pricing.promotion.PromotionManager promotionManager;
    private com.pricingos.pricing.pricelist.PriceListManager priceListManager;
    private com.pricingos.pricing.approval.ApprovalWorkflowEngine approvalEngine;
    private com.pricingos.pricing.approval.ProfitabilityAnalyticsObserver analyticsObserver;
    
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
            promotionManager = new com.pricingos.pricing.promotion.PromotionManager(skuCatalogService);
            priceListManager = new com.pricingos.pricing.pricelist.PriceListManager();

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

            log("Initialized subsystem APIs: PromotionManager, PriceListManager, ApprovalWorkflowEngine, AnalyticsObserver");
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
        tabbedPane.addTab("Price Calculator", createPricingCalculatorPanel());
        tabbedPane.addTab("Approval Workflows", createApprovalPanel());
        tabbedPane.addTab("Profitability Analytics", createAnalyticsPanel());
        
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
                    try {
                        MultiLevelPricingSubsystem.INSTANCE.onInvalidPromoCode(couponCode);
                    } catch (ExceptionInInitializerError | NoClassDefFoundError err) {
                        // Database not available during tests
                    }
                    log("ERROR: Invalid numeric input for promotion creation");
                    JOptionPane.showMessageDialog(this, "Error: Invalid numeric input. Please enter valid numbers for discount value, min cart value, and max uses.",
                        "Input Error", JOptionPane.ERROR_MESSAGE);
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
                log("Invalid promo code: " + couponCode + " - Reason: " + ex.getReason());
                JOptionPane.showMessageDialog(this, "Invalid promo code: " + couponCode + "\nReason: " + ex.getReason(),
                    "Validation Failed", JOptionPane.WARNING_MESSAGE);
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
        JTextField customerField = new JTextField("CUST-001");
        inputPanel.add(customerField);
        
        inputPanel.add(new JLabel("Quantity:"));
        JTextField quantityField = new JTextField("10");
        inputPanel.add(quantityField);
        
        inputPanel.add(new JLabel("Promo Code:"));
        JTextField promoField = new JTextField("SUMMER24");
        inputPanel.add(promoField);
        
        JButton calculateButton = new JButton("Calculate Price");
        calculateButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        calculateButton.addActionListener(e -> calculatePrice(skuField, customerField, quantityField, promoField));
        
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(calculateButton);
        
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
            log("Loading approval requests from database...");
            approvalTableModel.setRowCount(0);
            for (com.pricingos.pricing.approval.ApprovalRequest r : com.pricingos.pricing.approval.ApprovalRequestDao.findAll(null)) {
                approvalTableModel.addRow(new Object[]{ 
                    r.getApprovalId(), 
                    r.getOrderId(), 
                    r.getRequestType().name(),
                    r.getRequestedBy(), 
                    r.getRequestedDiscountAmt(), 
                    r.getJustificationText(),
                    r.getRoutedToApproverId(),
                    r.getSubmissionTime(), 
                    r.getStatus().name() 
                });
            }
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
        
        JLabel titleLabel = new JLabel("Pending Approval Requests");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        panel.add(titleLabel, BorderLayout.NORTH);
        
        String[] columns = {"ID", "Order", "Type", "Requested By", "Discount", "Justification", "Assigned To", "Submission Time", "Status"};
        approvalTableModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        approvalTable = new JTable(approvalTableModel);
        panel.add(new JScrollPane(approvalTable), BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel();
        JButton approveBtn = new JButton("Approve Request");
        approveBtn.addActionListener(e -> {
            int row = approvalTable.getSelectedRow();
            if (row >= 0) {
                String id = (String) approvalTableModel.getValueAt(row, 0);
                try {
                	approvalEngine.approve(id, "MANAGER_123");
                	loadData();
                	JOptionPane.showMessageDialog(panel, "Request Approved Successfully!");
                } catch(Exception ex) { JOptionPane.showMessageDialog(panel, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE); }
            } else { JOptionPane.showMessageDialog(panel, "Select a row first"); }
        });
        JButton rejectBtn = new JButton("Reject Request");
        rejectBtn.addActionListener(e -> {
            int row = approvalTable.getSelectedRow();
            if (row >= 0) {
                String id = (String) approvalTableModel.getValueAt(row, 0);
                try {
                    approvalEngine.reject(id, "MANAGER_123", "Manual review rejection");
                    loadData();
                    JOptionPane.showMessageDialog(panel, "Request Rejected!");
                } catch(Exception ex) { JOptionPane.showMessageDialog(panel, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE); }
            } else { JOptionPane.showMessageDialog(panel, "Select a row first"); }
        });
        buttonPanel.add(approveBtn);
        buttonPanel.add(rejectBtn);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        return panel;
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
            
            // Step 4: Apply Policy Rules (simplified)
            BigDecimal policyDiscount = BigDecimal.ZERO;
            if (afterPromo.compareTo(new BigDecimal("1000")) > 0) {
                policyDiscount = new BigDecimal("5.00");
                breakdown.append("Volume Policy: -").append(policyDiscount).append(" (5%)\n");
            }
            
            BigDecimal finalPrice = afterPromo.subtract(afterPromo.multiply(policyDiscount).divide(BigDecimal.valueOf(100)));
            breakdown.append("Final Price: ").append(finalPrice).append("\n\n");
            
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
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + java.time.LocalTime.now() + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
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
