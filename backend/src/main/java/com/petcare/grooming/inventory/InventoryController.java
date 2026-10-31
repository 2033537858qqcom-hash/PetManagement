package com.petcare.grooming.inventory;
import com.petcare.grooming.security.AccountAccess;
import com.petcare.grooming.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api")
public class InventoryController {
    private final InventoryService inventory;
    private final org.springframework.jdbc.core.JdbcTemplate db;
    private final AccountAccess access;
    InventoryController(InventoryService inventory, org.springframework.jdbc.core.JdbcTemplate db, AccountAccess access) {
        this.inventory=inventory; this.db=db; this.access=access;
    }
    @GetMapping("/inventory") public ApiResponse<?> list() {
        if (access.role("OWNER")) return ApiResponse.ok(db.queryForList("select id, name, stock, unit, price, sale_units, image_key from product where price > 0 order by id"));
        if (access.role("GROOMER")) return ApiResponse.ok(db.queryForList("select id, name, unit from product order by id"));
        access.requireStaff();
        return ApiResponse.ok(db.queryForList("select * from product order by id"));
    }
    @PostMapping("/inventory/{id}/adjust") public ApiResponse<?> adjust(@PathVariable long id, @RequestBody InventoryService.Adjustment form) { return ApiResponse.ok(inventory.adjust(id,form)); }
    @GetMapping("/inventory/{id}/flows") public ApiResponse<?> history(@PathVariable long id) { return ApiResponse.ok(inventory.history(id)); }
    @PutMapping("/inventory/{id}/retail") public ApiResponse<?> retail(@PathVariable long id, @RequestBody InventoryService.Retail form) { return ApiResponse.ok(inventory.retail(id,form)); }
    @GetMapping("/services/{id}/consumption") public ApiResponse<?> rules(@PathVariable long id) { return ApiResponse.ok(inventory.rules(id)); }
    @PutMapping("/services/{id}/consumption") public ApiResponse<?> configure(@PathVariable long id, @RequestBody InventoryService.Rules rules) { inventory.configure(id,rules); return ApiResponse.ok(inventory.rules(id)); }
}
