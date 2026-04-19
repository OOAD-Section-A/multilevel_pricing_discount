package com.pricingos.pricing.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import com.pricingos.pricing.approval.AuditLogObserver.AuditEntry;
import com.pricingos.pricing.approval.ProfitabilityAnalyticsObserver.ProfitabilityEntry;
import com.pricingos.pricing.discount.DiscountPolicy;
import com.pricingos.common.VolumeTierRule;
import com.pricingos.common.ApprovalRequestType;
import com.pricingos.common.ApprovalStatus;

public class DaoBulk {
    private static Connection conn() throws SQLException { return DatabaseConnectionPool.getInstance().getConnection(); }

    public static class AuditLogDao {
        public static void save(AuditEntry e) {
            String sql = "INSERT INTO audit_log (approval_id, timestamp, event_type, actor, detail) VALUES (?, ?, ?, ?, ?)";
            try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, e.approvalId());
                ps.setObject(2, e.timestamp());
                ps.setString(3, e.eventType());
                ps.setString(4, e.actor());
                ps.setString(5, e.detail());
                ps.executeUpdate();
            } catch (Exception ex) { throw new RuntimeException(ex); }
        }
        public static List<AuditEntry> findAll() {
            List<AuditEntry> l = new ArrayList<>();
            try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("SELECT * FROM audit_log"); ResultSet rs = ps.executeQuery()) {
                while(rs.next()) {
                    l.add(new AuditEntry(rs.getTimestamp("timestamp").toLocalDateTime(), rs.getString("approval_id"), rs.getString("event_type"), rs.getString("actor"), rs.getString("detail")));
                }
            } catch(Exception ex) {}
            return l;
        }
    }

    public static class AnalyticsDao {
        public static void save(ProfitabilityEntry e) {
            String sql = "INSERT INTO profitability_analytics (approval_id, request_type, discount_amount, final_status, recorded_at) VALUES (?, ?, ?, ?, ?)";
            try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, e.approvalId());
                ps.setString(2, e.requestType().name());
                ps.setDouble(3, e.discountAmount());
                ps.setString(4, e.finalStatus().name());
                ps.setObject(5, e.recordedAt());
                ps.executeUpdate();
            } catch (Exception ex) { throw new RuntimeException(ex); }
        }
        public static List<ProfitabilityEntry> findAll() {
            List<ProfitabilityEntry> l = new ArrayList<>();
            try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("SELECT * FROM profitability_analytics"); ResultSet rs = ps.executeQuery()) {
                while(rs.next()) {
                    l.add(new ProfitabilityEntry(rs.getString("approval_id"), 
                        ApprovalRequestType.valueOf(rs.getString("request_type")), 
                        rs.getDouble("discount_amount"), 
                        ApprovalStatus.valueOf(rs.getString("final_status")), 
                        rs.getTimestamp("recorded_at").toLocalDateTime()));
                }
            } catch(Exception ex) {}
            return l;
        }
    }

    public static class PolicyDao {
        public static void save(DiscountPolicy p) {
            String sql = "INSERT INTO discount_policies (policy_id, policy_name, discount_rate, active_flag, min_order_value, requires_approval) VALUES (?,?,?,?,?,?) ON DUPLICATE KEY UPDATE policy_name=?, active_flag=?";
            try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, p.getPolicyId());
                ps.setString(2, p.getPolicyName());
                ps.setDouble(3, 0.0);
                ps.setBoolean(4, p.isActive());
                ps.setDouble(5, 0.0);
                ps.setBoolean(6, false);
                ps.setString(7, p.getPolicyName());
                ps.setBoolean(8, p.isActive());
                ps.executeUpdate();
            } catch(Exception e){ throw new RuntimeException(e); }
        }
        public static List<DiscountPolicy> findAll() {
            List<DiscountPolicy> l = new ArrayList<>();
            try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("SELECT * FROM discount_policies"); ResultSet rs = ps.executeQuery()) {
                while(rs.next()) {
                    DiscountPolicy p = DiscountPolicy.builder(rs.getString("policy_name")).isActive(rs.getBoolean("active_flag")).build();
                    Field f = DiscountPolicy.class.getDeclaredField("policyId"); f.setAccessible(true); f.set(p, rs.getString("policy_id"));
                    l.add(p);
                }
            } catch(Exception ex) {}
            return l;
        }
    }

    public static void clearAll() {
        BundleDao.mockMap.clear();
        RebateDao.mockMap.clear();
        VolumeDao.mockMap.clear();
        try (Connection c = conn(); java.sql.Statement s = c.createStatement()) {
            s.execute("SET FOREIGN_KEY_CHECKS = 0");
            s.execute("DELETE FROM approval_requests");
            s.execute("DELETE FROM audit_log");
            s.execute("DELETE FROM profitability_analytics");
            s.execute("DELETE FROM promotions");
            s.execute("DELETE FROM promotion_eligible_skus");
            s.execute("SET FOREIGN_KEY_CHECKS = 1");
        } catch(Exception e) {}
    }

    public static class BundleDao {
        private static final Map<String, Object> mockMap = new HashMap<>();
        public static void save(Object promo) {
            try {
                Field idF = promo.getClass().getDeclaredField("promoId"); idF.setAccessible(true);
                mockMap.put((String)idF.get(promo), promo);
            } catch(Exception e){}
        }
        public static List<Object> findAll(Class<?> bundleClass) { return new ArrayList<>(mockMap.values()); }
    }

    public static class RebateDao {
        private static final Map<String, Object> mockMap = new HashMap<>();
        public static void save(Object p) {
            try {
                Field idF = p.getClass().getDeclaredField("programId"); idF.setAccessible(true);
                mockMap.put((String)idF.get(p), p);
            } catch(Exception e){}
        }
        public static Object get(String id, Class<?> cz) { return mockMap.get(id); }
    }

    public static class VolumeDao {
        private static final Map<String, Object> mockMap = new HashMap<>();
        public static void save(String sku, Object sched) { mockMap.put(sku, sched); }
        public static Object get(String sku, Class<?> cz) { return mockMap.get(sku); }
        public static boolean has(String sku) { return mockMap.containsKey(sku); }
    }

    public static class PromoDao {
        public static void save(Object p) {
            try (Connection c = conn()) {
                c.setAutoCommit(false);
                Class<?> cz = p.getClass();
                
                Field fId = cz.getDeclaredField("promoId"); fId.setAccessible(true);
                Field fCode = cz.getDeclaredField("couponCode"); fCode.setAccessible(true);
                Field fType = cz.getDeclaredField("discountType"); fType.setAccessible(true);
                Field fVal = cz.getDeclaredField("discountValue"); fVal.setAccessible(true);
                Field fStart = cz.getDeclaredField("startDate"); fStart.setAccessible(true);
                Field fEnd = cz.getDeclaredField("endDate"); fEnd.setAccessible(true);
                Field fSkus = cz.getDeclaredField("eligibleSkuIds"); fSkus.setAccessible(true);
                Field fMinCart = cz.getDeclaredField("minCartValue"); fMinCart.setAccessible(true);
                Field fMax = cz.getDeclaredField("maxUses"); fMax.setAccessible(true);
                Field fCur = cz.getDeclaredField("currentUseCount"); fCur.setAccessible(true);
                Field fExp = cz.getDeclaredField("expired"); fExp.setAccessible(true);
                Field fName = cz.getDeclaredField("name"); fName.setAccessible(true);

                String id = (String)fId.get(p);
                
                String sql = "INSERT INTO promotions (promo_id, promo_name, coupon_code, start_date, end_date, min_cart_value, max_uses, current_use_count, expired, discount_type, discount_value, eligible_sku_ids) " +
                             "VALUES (?,?,?,?,?,?,?,?,?,?,?,?) " +
                             "ON DUPLICATE KEY UPDATE current_use_count=?, expired=?";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setString(1, id);
                    ps.setString(2, (String)fName.get(p));
                    ps.setString(3, (String)fCode.get(p));
                    ps.setObject(4, fStart.get(p));
                    ps.setObject(5, fEnd.get(p));
                    ps.setDouble(6, fMinCart.getDouble(p));
                    ps.setInt(7, fMax.getInt(p) == 0 ? Integer.MAX_VALUE : fMax.getInt(p));
                    ps.setInt(8, fCur.getInt(p));
                    ps.setBoolean(9, fExp.getBoolean(p));
                    ps.setString(10, ((Enum<?>)fType.get(p)).name());
                    ps.setDouble(11, fVal.getDouble(p));
                    
                    List<String> skus = (List<String>)fSkus.get(p);
                    String skusJson = skus.stream().map(s -> "\"" + s + "\"").collect(java.util.stream.Collectors.joining(",", "[", "]"));
                    ps.setString(12, skusJson);
                    
                    ps.setInt(13, fCur.getInt(p));
                    ps.setBoolean(14, fExp.getBoolean(p));
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = c.prepareStatement("DELETE FROM promotion_eligible_skus WHERE promo_id=?")) {
                    ps.setString(1, id);
                    ps.executeUpdate();
                }
                
                List<String> skus = (List<String>)fSkus.get(p);
                if (skus != null && !skus.isEmpty()) {
                    try (PreparedStatement ps = c.prepareStatement("INSERT INTO promotion_eligible_skus (promo_id, sku_id) VALUES (?,?)")) {
                        for (String sku : skus) {
                            ps.setString(1, id);
                            ps.setString(2, sku);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
                c.commit();
            } catch(Exception e) { throw new RuntimeException(e); }
        }

        private static Object mapRow(ResultSet rs, Connection c, Class<?> cz) throws Exception {
            List<String> skus = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT sku_id FROM promotion_eligible_skus WHERE promo_id=?")) {
                ps.setString(1, rs.getString("promo_id"));
                try (ResultSet rsSkus = ps.executeQuery()) {
                    while (rsSkus.next()) skus.add(rsSkus.getString("sku_id"));
                }
            }
            
            Constructor<?> ctor = cz.getDeclaredConstructors()[0];
            ctor.setAccessible(true);
            
            Class<?> dtClass = Class.forName("com.pricingos.common.DiscountType");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object type = Enum.valueOf((Class<Enum>)dtClass, rs.getString("discount_type"));
            
            Object p = ctor.newInstance(
                rs.getString("promo_id"),
                rs.getString("promo_name") != null ? rs.getString("promo_name") : "DBLoad",
                rs.getString("coupon_code"),
                type,
                rs.getDouble("discount_value"),
                rs.getObject("start_date", LocalDate.class),
                rs.getObject("end_date", LocalDate.class),
                skus,
                rs.getDouble("min_cart_value"),
                rs.getInt("max_uses")
            );
            
            Field fCur = cz.getDeclaredField("currentUseCount"); fCur.setAccessible(true);
            fCur.setInt(p, rs.getInt("current_use_count"));
            Field fExp = cz.getDeclaredField("expired"); fExp.setAccessible(true);
            fExp.setBoolean(p, rs.getBoolean("expired"));
            
            return p;
        }

        public static Object getByCode(String code, Class<?> cz) {
            try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("SELECT * FROM promotions WHERE coupon_code=?")) {
                ps.setString(1, code);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapRow(rs, c, cz);
                }
            } catch(Exception e) { throw new RuntimeException(e); }
            return null;
        }
        
        public static List<Object> findAll(Class<?> cz) {
            List<Object> res = new ArrayList<>();
            try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("SELECT * FROM promotions")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) res.add(mapRow(rs, c, cz));
                }
            } catch (Exception e) { throw new RuntimeException(e); }
            return res;
        }
    }
}
