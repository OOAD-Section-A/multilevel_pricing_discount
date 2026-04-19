-- ============================================================
-- populate_part1.sql — Seed data for Subsystems 1-5 + UI
-- Run AFTER schema.sql. 2 rows per table minimum.
-- Dependency order: parent tables before children.
-- ============================================================
USE OOAD;
SET FOREIGN_KEY_CHECKS = 0;

-- Populating: price_list
INSERT INTO price_list (price_id, sku_id, region_code, channel, price_type, base_price, price_floor, currency_code, effective_from, effective_to, status) VALUES
('PL001','SKU-1001','SOUTH','RETAIL','RETAIL',1500.00,1200.00,'INR','2025-01-01 00:00:00','2026-12-31 23:59:59','ACTIVE'),
('PL002','SKU-1002','NORTH','DISTRIBUTOR','DISTRIBUTOR',2500.00,2000.00,'INR','2025-01-01 00:00:00','2026-12-31 23:59:59','ACTIVE');

-- Populating: tier_definitions (parent of customer_segmentation)
INSERT INTO tier_definitions (tier_id, tier_name, min_spend_threshold, default_discount_pct) VALUES
(1,'STANDARD',0.00,0.00),
(2,'SILVER',10000.00,5.00),
(3,'GOLD',50000.00,10.00),
(4,'PLATINUM',100000.00,15.00);

-- Populating: customer_segmentation
INSERT INTO customer_segmentation (segmentation_id, customer_id, cumulative_spend, historical_order_totals, assigned_tier_id, manual_override, override_tier_id) VALUES
('SEG001','CUST-001',75000.00,75000.00,3,FALSE,NULL),
('SEG002','CUST-002',120000.00,120000.00,4,TRUE,4);

-- Populating: price_configuration
INSERT INTO price_configuration (price_config_id, sku_id, cogs_value, desired_margin_pct, computed_base_price, product_attributes) VALUES
('PC001','SKU-1001',900.00,40.00,1500.00,'Category=Electronics'),
('PC002','SKU-1002',1500.00,40.00,2500.00,'Category=Appliances');

-- Populating: discount_rule_results
INSERT INTO discount_rule_results (order_line_id, order_id, quantity, batch_expiry_date, final_price, applied_discount_pct, discount_breakdown) VALUES
('OL001','ORD-001',5,NULL,1350.00,10.00,'Tier Gold 10%'),
('OL002','ORD-002',10,'2026-06-15 00:00:00',2125.00,15.00,'Tier Platinum 15%');

-- Populating: promotions (parent of promotion_eligible_skus)
INSERT INTO promotions (promo_id, promo_name, coupon_code, discount_type, discount_value, start_date, end_date, eligible_sku_ids, min_cart_value, max_uses, current_use_count, expired) VALUES
('PROMO001','Summer Sale','SUMMER25','PERCENTAGE_OFF',25.00,'2025-06-01 00:00:00','2025-08-31 23:59:59','["SKU-1001","SKU-1002"]',500.00,1000,50,FALSE),
('PROMO002','New Year Deal','NEWYEAR10','FIXED_AMOUNT',500.00,'2025-12-20 00:00:00','2026-01-10 23:59:59','["SKU-1001"]',1000.00,500,10,FALSE);

-- Populating: promotion_eligible_skus
INSERT INTO promotion_eligible_skus (promo_id, sku_id) VALUES
('PROMO001','SKU-1001'),
('PROMO001','SKU-1002');

-- Populating: bundle_promotions
INSERT INTO bundle_promotions (promo_id, promo_name, discount_pct, start_date, end_date, expired) VALUES
('BNDL001','Electronics Combo',0.1200,'2025-06-01','2025-12-31',FALSE),
('BNDL002','Appliance Bundle',0.0800,'2025-07-01','2026-03-31',FALSE);

-- Populating: bundle_promotion_skus
INSERT INTO bundle_promotion_skus (promo_id, sku_id) VALUES
('BNDL001','SKU-1001'),
('BNDL002','SKU-1002');

-- Populating: discount_policies
INSERT INTO discount_policies (policy_id, policy_name, stacking_rule, priority_level, max_discount_cap_pct, perishability_days, clearance_discount_pct, is_active) VALUES
('POL001','Standard Stacking','ADDITIVE',1,30.00,30,20.00,TRUE),
('POL002','Premium Exclusive','EXCLUSIVE',2,50.00,15,35.00,TRUE);

-- Populating: rebate_programs
INSERT INTO rebate_programs (program_id, customer_id, sku_id, target_spend, accumulated_spend, rebate_pct) VALUES
('REB001','CUST-001','SKU-1001',50000.0000,32000.0000,0.0300),
('REB002','CUST-002','SKU-1002',80000.0000,80000.0000,0.0500);

-- Populating: volume_discount_schedules (parent of volume_tier_rules)
INSERT INTO volume_discount_schedules (schedule_id, sku_id) VALUES
('VDS001','SKU-1001'),
('VDS002','SKU-1002');

-- Populating: volume_tier_rules
INSERT INTO volume_tier_rules (schedule_id, min_qty, max_qty, discount_pct) VALUES
('VDS001',1,9,0.0000),
('VDS001',10,50,0.0500);

-- Populating: customer_tier_cache
INSERT INTO customer_tier_cache (customer_id, tier) VALUES
('CUST-001','GOLD'),
('CUST-002','PLATINUM');

-- Populating: customer_tier_overrides
INSERT INTO customer_tier_overrides (customer_id, override_tier) VALUES
('CUST-002','PLATINUM');

-- Populating: regional_pricing_multipliers
INSERT INTO regional_pricing_multipliers (region_code, multiplier) VALUES
('SOUTH',1.0000),
('NORTH',1.0500);

-- Populating: contract_pricing
INSERT INTO contract_pricing (contract_id, contract_customer_id, contract_sku_id, negotiated_price, contract_start_date, contract_expiry_date, contract_status) VALUES
('CPRC001','CUST-001','SKU-1001',1350.00,'2025-01-01 00:00:00','2026-12-31 23:59:59','ACTIVE'),
('CPRC002','CUST-002','SKU-1002',2200.00,'2025-03-01 00:00:00','2026-06-30 23:59:59','ACTIVE');

-- Populating: contracts (parent of contract_sku_prices)
INSERT INTO contracts (contract_id, customer_id, status, start_date, end_date) VALUES
('CON001','CUST-001','ACTIVE','2025-01-01','2026-12-31'),
('CON002','CUST-002','ACTIVE','2025-03-01','2026-06-30');

-- Populating: contract_sku_prices
INSERT INTO contract_sku_prices (contract_id, sku_id, negotiated_price) VALUES
('CON001','SKU-1001',1350.0000),
('CON002','SKU-1002',2200.0000);

-- Populating: price_approvals
INSERT INTO price_approvals (approval_id, request_type, requested_by, requested_discount_amt, justification_text, approving_manager_id, approval_status, approval_timestamp, audit_log_flag) VALUES
('APR001','MANUAL_DISCOUNT','EMP-101',150.00,'Loyal customer retention','MGR-001','APPROVED','2025-04-10 14:30:00',TRUE),
('APR002','POLICY_EXCEPTION','EMP-102',300.00,'Bulk order special pricing','MGR-002','REJECTED','2025-04-12 09:15:00',TRUE);

-- Populating: approval_requests (parent of audit_log, profitability_analytics)
INSERT INTO approval_requests (approval_id, request_type, order_id, requested_discount_amt, status, submission_time, approval_timestamp, routed_to_approver_id, approving_manager_id, rejection_reason, audit_log_flag) VALUES
('a0000001-0001-0001-0001-000000000001','MANUAL_DISCOUNT','ORD-001',150.0000,'APPROVED','2025-04-10 10:00:00','2025-04-10 14:30:00','MGR-001','MGR-001',NULL,TRUE),
('a0000001-0001-0001-0001-000000000002','POLICY_EXCEPTION','ORD-002',300.0000,'REJECTED','2025-04-12 08:00:00','2025-04-12 09:15:00','MGR-002','MGR-002','Exceeds margin floor',TRUE);

-- Populating: audit_log
INSERT INTO audit_log (approval_id, timestamp, event_type, actor, detail) VALUES
('a0000001-0001-0001-0001-000000000001','2025-04-10 14:30:00','APPROVED','MGR-001','Approved discount for CUST-001'),
('a0000001-0001-0001-0001-000000000002','2025-04-12 09:15:00','REJECTED','MGR-002','Rejected: exceeds margin floor');

-- Populating: profitability_analytics
INSERT INTO profitability_analytics (approval_id, request_type, discount_amount, final_status, recorded_at) VALUES
('a0000001-0001-0001-0001-000000000001','MANUAL_DISCOUNT',150.0000,'APPROVED','2025-04-10 14:30:00'),
('a0000001-0001-0001-0001-000000000002','POLICY_EXCEPTION',300.0000,'REJECTED','2025-04-12 09:15:00');

-- Populating: warehouses (parent of warehouse_zones)
INSERT INTO warehouses (warehouse_id, warehouse_name) VALUES
('WH001','Chennai Central Warehouse'),
('WH002','Delhi North Warehouse');

-- Populating: warehouse_zones (parent of bins)
INSERT INTO warehouse_zones (zone_id, warehouse_id, zone_type) VALUES
('ZN001','WH001','STORAGE'),
('ZN002','WH002','PICKING');

-- Populating: bins (parent of stock_records, stock_movements)
INSERT INTO bins (bin_id, zone_id, bin_capacity, bin_status) VALUES
('BIN001','ZN001',500,'OCCUPIED'),
('BIN002','ZN002',300,'AVAILABLE');

-- Populating: goods_receipts
INSERT INTO goods_receipts (goods_receipt_id, purchase_order_id, supplier_id, product_id, ordered_qty, received_qty, condition_status) VALUES
('GR001','PO-001','SUP-001','PROD-001',100,100,'GOOD'),
('GR002','PO-002','SUP-002','PROD-002',200,195,'PARTIAL');

-- Populating: stock_records
INSERT INTO stock_records (stock_id, product_id, bin_id, quantity) VALUES
('STK001','PROD-001','BIN001',100),
('STK002','PROD-002','BIN002',195);

-- Populating: stock_movements
INSERT INTO stock_movements (movement_id, movement_type, from_bin, to_bin, product_id, moved_qty) VALUES
('MOV001','INBOUND',NULL,'BIN001','PROD-001',100),
('MOV002','TRANSFER','BIN001','BIN002','PROD-001',20);

-- Populating: pick_tasks
INSERT INTO pick_tasks (pick_task_id, order_id, assigned_employee_id, product_id, pick_qty, task_status) VALUES
('PT001','ORD-001','EMP-201','PROD-001',5,'COMPLETED'),
('PT002','ORD-002','EMP-202','PROD-002',10,'PENDING');

-- Populating: staging_dispatch
INSERT INTO staging_dispatch (staging_id, dock_door_id, order_id, dispatched_at, shipment_status) VALUES
('STG001','DOCK-A','ORD-001','2025-04-11 08:00:00','DISPATCHED'),
('STG002','DOCK-B','ORD-002',NULL,'STAGED');

-- Populating: warehouse_returns
INSERT INTO warehouse_returns (return_id, product_id, return_qty, condition_status) VALUES
('RET001','PROD-001',2,'GOOD'),
('RET002','PROD-002',5,'DAMAGED');

-- Populating: cycle_counts
INSERT INTO cycle_counts (cycle_count_id, product_id, product_name, sku, employee_id, employee_name, expected_qty, counted_qty) VALUES
('CC001','PROD-001','Wireless Mouse','SKU-1001','EMP-201','Ravi Kumar',100,98),
('CC002','PROD-002','Smart Speaker','SKU-1002','EMP-202','Priya Sharma',195,195);

-- Populating: ui_users (parent of ui_sessions, ui_panel_state, ui_notifications, ui_notification_preferences)
INSERT INTO ui_users (username, password_hash, user_role, user_email, user_display_name) VALUES
('admin','$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ012','ADMIN','admin@pricingos.com','System Admin'),
('manager1','$2a$10$xyzdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ012','MANAGER','mgr1@pricingos.com','Arun Manager');

-- Populating: ui_sessions
INSERT INTO ui_sessions (user_id, jwt_session_token, session_expiry_time) VALUES
(1,'eyJhbGciOiJIUzI1NiJ9.eyJ1c2VyIjoiYWRtaW4ifQ.token1',1745000000000),
(2,'eyJhbGciOiJIUzI1NiJ9.eyJ1c2VyIjoibWdyMSJ9.token2',1745000000000);

-- Populating: ui_panel_state
INSERT INTO ui_panel_state (user_id, panel_id, current_panel_state) VALUES
(1,'PRICING_DASHBOARD','OPEN'),
(2,'APPROVAL_QUEUE','OPEN');

-- Populating: ui_notifications
INSERT INTO ui_notifications (user_id, notification_type, notification_message) VALUES
(1,'SYSTEM','System startup completed successfully'),
(2,'APPROVAL','New approval request APR001 pending review');

-- Populating: ui_audit_log
INSERT INTO ui_audit_log (audit_action_user, audit_action_description, audit_module_name) VALUES
('admin','Logged in successfully','AUTH'),
('manager1','Approved discount request APR001','PRICING');

-- Populating: ui_notification_preferences
INSERT INTO ui_notification_preferences (user_id, pref_key, pref_value) VALUES
(1,'EMAIL_ON_LOW_STOCK',TRUE),
(2,'EMAIL_ON_APPROVAL',TRUE);

-- Populating: ui_system_config
INSERT INTO ui_system_config (config_key, config_value) VALUES
('MAX_DISCOUNT_PCT','50'),
('SESSION_TIMEOUT_MINUTES','30');

-- Populating: stock_ledger_entries
INSERT INTO stock_ledger_entries (transaction_id, transaction_type, item_name, quantity, unit, debit_account, credit_account, entry_date, reference_number, total_debit, total_credit, balance_status) VALUES
('TXN001','INBOUND','Wireless Mouse',100,'PCS','Inventory:PROD-001','GoodsReceipt:GR001','2025-04-01','PO-001',150000.00,150000.00,'BALANCED'),
('TXN002','SALE','Smart Speaker',10,'PCS','COGS:PROD-002','Inventory:PROD-002','2025-04-05','ORD-002',25000.00,25000.00,'BALANCED');

SET FOREIGN_KEY_CHECKS = 1;
