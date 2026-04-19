-- ============================================================
-- populate_part2.sql — Seed data for Inventory, Orders,
-- Delivery, Commission, Forecasting, and remaining tables.
-- Run AFTER populate_part1.sql.
-- ============================================================
USE OOAD;
SET FOREIGN_KEY_CHECKS = 0;

-- Populating: stock_levels
INSERT INTO stock_levels (stock_level_id, product_id, current_stock_qty, reserved_stock_qty, available_stock_qty, reorder_threshold, reorder_quantity, safety_stock_level) VALUES
('SL001','PROD-001',100,15,85,20,50,10),
('SL002','PROD-002',195,30,165,25,60,15);

-- Populating: products
INSERT INTO products (product_id, product_name, sku, category, sub_category, supplier_id, unit_of_measure) VALUES
('PROD-001','Wireless Mouse','SKU-1001','Electronics','Peripherals','SUP-001','PCS'),
('PROD-002','Smart Speaker','SKU-1002','Electronics','Audio','SUP-002','PCS');

-- Populating: product_batches
INSERT INTO product_batches (batch_id, product_id, lot_id, manufacturing_date, supplier_id, batch_status, quantity_received, expiry_date, received_date) VALUES
('BAT001','PROD-001','LOT-A1','2025-01-15','SUP-001','ACTIVE',100,'2027-01-15','2025-02-01 10:00:00'),
('BAT002','PROD-002','LOT-B1','2025-02-20','SUP-002','ACTIVE',200,'2027-02-20','2025-03-05 10:00:00');

-- Populating: expiry_tracking
INSERT INTO expiry_tracking (expiry_id, batch_id, expiry_date, days_remaining, expiry_status, alert_flag) VALUES
('EXP001','BAT001','2027-01-15',630,'VALID',FALSE),
('EXP002','BAT002','2027-02-20',666,'VALID',FALSE);

-- Populating: stock_adjustments
INSERT INTO stock_adjustments (adjustment_id, product_id, batch_id, adjustment_type, quantity_adjusted, reason, adjusted_by) VALUES
('ADJ001','PROD-001','BAT001','DECREASE',2,'Damaged in transit','EMP-201'),
('ADJ002','PROD-002',NULL,'INCREASE',5,'Recount correction','EMP-202');

-- Populating: reorder_management
INSERT INTO reorder_management (reorder_id, product_id, current_stock, reorder_threshold, reorder_quantity, supplier_id, reorder_status) VALUES
('REO001','PROD-001',85,20,50,'SUP-001','PENDING'),
('REO002','PROD-002',165,25,60,'SUP-002','ORDERED');

-- Populating: stock_reservations
INSERT INTO stock_reservations (reservation_id, product_id, order_id, reserved_qty, reservation_status, reserved_at) VALUES
('RSV001','PROD-001','ORD-001',5,'ACTIVE','2025-04-10 09:00:00'),
('RSV002','PROD-002','ORD-002',10,'ACTIVE','2025-04-12 07:30:00');

-- Populating: stock_freeze
INSERT INTO stock_freeze (freeze_id, product_id, batch_id, freeze_status, freeze_reason, frozen_by, frozen_at) VALUES
('FRZ001','PROD-001','BAT001',FALSE,'Quality check passed','EMP-301','2025-03-15 12:00:00'),
('FRZ002','PROD-002',NULL,TRUE,'Regulatory hold pending','EMP-301','2025-04-01 08:00:00');

-- Populating: dead_stock
INSERT INTO dead_stock (dead_stock_id, product_id, last_movement_date, stagnant_days, stagnant_quantity, action_flag) VALUES
('DS001','PROD-001','2025-01-01 00:00:00',90,10,'HOLD'),
('DS002','PROD-002','2024-10-01 00:00:00',180,25,'CLEARANCE');

-- Populating: stock_valuation
INSERT INTO stock_valuation (valuation_id, product_id, unit_cost, total_quantity, total_value, reserved_value, valuation_method) VALUES
('VAL001','PROD-001',900.00,100,90000.00,4500.00,'FIFO'),
('VAL002','PROD-002',1500.00,195,292500.00,15000.00,'AVG');

-- Populating: orders (parent of order_items, delivery_orders, shipments, packaging_jobs, repair_requests, receipt_records, product_returns)
INSERT INTO orders (order_id, customer_id, order_status, order_date, total_amount, payment_status, sales_channel) VALUES
('ORD-001','CUST-001','CONFIRMED','2025-04-10 09:00:00',6750.00,'PAID','ONLINE'),
('ORD-002','CUST-002','PLACED','2025-04-12 07:30:00',21250.00,'PENDING','POS');

-- Populating: order_items
INSERT INTO order_items (order_item_id, order_id, product_id, ordered_quantity, unit_price, line_total) VALUES
('OI001','ORD-001','PROD-001',5,1350.00,6750.00),
('OI002','ORD-002','PROD-002',10,2125.00,21250.00);

-- Populating: fulfillment_orders
INSERT INTO fulfillment_orders (fulfillment_id, order_id, customer_id, product_id, quantity, fulfillment_status, assigned_warehouse, priority_level, created_at) VALUES
('FUL001','ORD-001','CUST-001','PROD-001',5,'IN_PROGRESS','WH001','HIGH','2025-04-10 09:30:00'),
('FUL002','ORD-002','CUST-002','PROD-002',10,'PENDING','WH002','MEDIUM','2025-04-12 08:00:00');

-- Populating: packing_details
INSERT INTO packing_details (packing_id, fulfillment_id, package_type, packed_by, packed_at, package_weight) VALUES
('PKG001','FUL001','BOX_MEDIUM','EMP-201','2025-04-10 11:00:00',2.50),
('PKG002','FUL002','BOX_LARGE','EMP-202','2025-04-12 10:00:00',8.00);

-- Populating: delivery_orders (parent of delivery_tracking_routes, delivery_tracking_events)
INSERT INTO delivery_orders (delivery_id, order_id, customer_id, delivery_address, delivery_status, delivery_date, delivery_type, delivery_cost, warehouse_id, created_at) VALUES
('DEL001','ORD-001','CUST-001','42 Anna Nagar, Chennai 600040','DELIVERED','2025-04-12 14:00:00','STANDARD',150.00,'WH001','2025-04-10 12:00:00'),
('DEL002','ORD-002','CUST-002','15 Connaught Place, Delhi 110001','PENDING',NULL,'EXPRESS',250.00,'WH002','2025-04-12 11:00:00');

-- Populating: delivery_tracking_routes (parent of delivery_tracking_waypoints)
INSERT INTO delivery_tracking_routes (route_plan_id, delivery_id, order_id, route_status) VALUES
('RTR001','DEL001','ORD-001','COMPLETED'),
('RTR002','DEL002','ORD-002','PLANNED');

-- Populating: delivery_tracking_waypoints
INSERT INTO delivery_tracking_waypoints (waypoint_id, route_plan_id, waypoint_sequence, waypoint_location) VALUES
('WP001','RTR001',1,'Chennai Central Warehouse'),
('WP002','RTR002',1,'Delhi North Warehouse');

-- Populating: delivery_tracking_events
INSERT INTO delivery_tracking_events (tracking_event_id, delivery_id, timeline_stage, event_timestamp) VALUES
('EVT001','DEL001','DELIVERED','2025-04-12 14:00:00'),
('EVT002','DEL002','DISPATCHED','2025-04-12 15:00:00');

-- Populating: agents
INSERT INTO agents (agent_id, agent_name, level, parent_agent_id, status) VALUES
('AGT001','Vikram Sales Lead',1,NULL,'ACTIVE'),
('AGT002','Meera Sales Rep',2,'AGT001','ACTIVE');

-- Populating: commission_sales
INSERT INTO commission_sales (sale_id, agent_id, sale_amount, sale_date, status) VALUES
('CS001','AGT001',6750.00,'2025-04-10 09:00:00','COMPLETED'),
('CS002','AGT002',21250.00,'2025-04-12 07:30:00','COMPLETED');

-- Populating: commission_tiers
INSERT INTO commission_tiers (tier_id, tier_level, min_sales, max_sales, commission_pct) VALUES
('CT001',1,0.00,50000.00,3.00),
('CT002',2,50000.01,NULL,5.00);

-- Populating: commission_history
INSERT INTO commission_history (commission_id, agent_id, period_start, period_end, total_sales, total_commission, calculated_at) VALUES
('CH001','AGT001','2025-04-01','2025-04-30',6750.00,202.50,'2025-05-01 00:00:00'),
('CH002','AGT002','2025-04-01','2025-04-30',21250.00,1062.50,'2025-05-01 00:00:00');

-- Populating: shipments (parent of logistics_routes, shipment_alerts)
INSERT INTO shipments (shipment_id, order_id, origin_address, destination_address, package_weight, shipping_priority, shipment_status) VALUES
('SHP001','ORD-001','Chennai Central Warehouse','42 Anna Nagar, Chennai',2.50,'STANDARD','DELIVERED'),
('SHP002','ORD-002','Delhi North Warehouse','15 Connaught Place, Delhi',8.00,'EXPRESS','IN_TRANSIT');

-- Populating: logistics_routes
INSERT INTO logistics_routes (route_id, shipment_id, route_status) VALUES
('LR001','SHP001','COMPLETED'),
('LR002','SHP002','PLANNED');

-- Populating: shipment_alerts
INSERT INTO shipment_alerts (alert_id, shipment_id, alert_message, alert_severity) VALUES
('SA001','SHP001','Delivered on time','INFO'),
('SA002','SHP002','Weather delay expected in route','WARNING');

-- Populating: packaging_jobs
INSERT INTO packaging_jobs (package_id, order_id, quantity, total_amount, packaging_status) VALUES
('PKJOB001','ORD-001',5,6750.00,'COMPLETED'),
('PKJOB002','ORD-002',10,21250.00,'PENDING');

-- Populating: repair_requests
INSERT INTO repair_requests (request_id, order_id, product_id, defect_details, request_status) VALUES
('RPR001','ORD-001','PROD-001','Scroll wheel intermittent','OPEN'),
('RPR002','ORD-002','PROD-002','Speaker buzzing at high volume','OPEN');

-- Populating: receipt_records
INSERT INTO receipt_records (receipt_record_id, order_id, package_id, received_amount, receipt_status) VALUES
('REC001','ORD-001','PKJOB001',6750.00,'CONFIRMED'),
('REC002','ORD-002','PKJOB002',21250.00,'PENDING');

-- Populating: product_returns (parent of return_growth_statistics)
INSERT INTO product_returns (return_request_id, order_id, customer_id, product_details, return_status) VALUES
('PRET001','ORD-001','CUST-001','Wireless Mouse - defective scroll','PROCESSING'),
('PRET002','ORD-002','CUST-002','Smart Speaker - buzzing issue','INITIATED');

-- Populating: return_growth_statistics
INSERT INTO return_growth_statistics (growth_stat_id, return_request_id, metric_period) VALUES
('RGS001','PRET001','MONTHLY'),
('RGS002','PRET002','QUARTERLY');

-- Populating: sales_records
INSERT INTO sales_records (sale_id, product_id, store_id, sale_date, quantity_sold, unit_price, revenue) VALUES
('SR001','PROD-001','STORE-001','2025-04-10',5,1350.00,6750.00),
('SR002','PROD-002','STORE-002','2025-04-12',10,2125.00,21250.00);

-- Populating: holiday_calendar
INSERT INTO holiday_calendar (holiday_id, holiday_date, holiday_name, holiday_type) VALUES
('HOL001','2025-01-26','Republic Day','NATIONAL'),
('HOL002','2025-08-15','Independence Day','NATIONAL');

-- Populating: promotional_calendar
INSERT INTO promotional_calendar (promo_calendar_id, promo_name, promo_start_date, promo_end_date) VALUES
('PCAL001','Diwali Sale','2025-10-15','2025-11-05'),
('PCAL002','Pongal Offer','2026-01-10','2026-01-18');

-- Populating: product_metadata
INSERT INTO product_metadata (product_id, product_name, category) VALUES
('PROD-001','Wireless Mouse','Electronics'),
('PROD-002','Smart Speaker','Electronics');

-- Populating: product_lifecycle_stages
INSERT INTO product_lifecycle_stages (lifecycle_id, product_id, current_stage, stage_start_date) VALUES
('PLS001','PROD-001','GROWTH','2025-01-01'),
('PLS002','PROD-002','MATURITY','2024-06-01');

-- Populating: inventory_supply
INSERT INTO inventory_supply (product_id, current_stock, reorder_point, lead_time_days, supplier_id) VALUES
('PROD-001',85,20,7,'SUP-001'),
('PROD-002',165,25,10,'SUP-002');

-- Populating: demand_forecasts (parent of forecast_performance_metrics)
INSERT INTO demand_forecasts (forecast_id, product_id, forecast_period, forecast_date, predicted_demand, confidence_score, reorder_signal, algorithm_used) VALUES
('DF001','PROD-001','MONTHLY','2025-05-01',120,85.50,TRUE,'ARIMA'),
('DF002','PROD-002','MONTHLY','2025-05-01',80,72.30,FALSE,'LSTM');

-- Populating: forecast_performance_metrics
INSERT INTO forecast_performance_metrics (eval_id, forecast_id, forecast_date, predicted_qty) VALUES
('FPM001','DF001','2025-05-01',120),
('FPM002','DF002','2025-05-01',80);

-- Populating: barcode_rfid_events
INSERT INTO barcode_rfid_events (event_id, product_id, rfid_tag, event_timestamp, status, source) VALUES
('BRE001','PROD-001','RFID-0001','2025-04-01 08:30:00','SCANNED','WAREHOUSE_GATE'),
('BRE002','PROD-002','RFID-0002','2025-04-02 09:15:00','SCANNED','LOADING_DOCK');

-- Populating: subsystem_exceptions
INSERT INTO subsystem_exceptions (exception_id, subsystem_name, severity, timestamp_utc, exception_message, status) VALUES
('EXC001','PRICING','LOW','2025-04-10 14:00:00','Discount cap exceeded warning','RESOLVED'),
('EXC002','WAREHOUSE','MEDIUM','2025-04-11 06:00:00','Bin capacity threshold reached','OPEN');

SET FOREIGN_KEY_CHECKS = 1;
