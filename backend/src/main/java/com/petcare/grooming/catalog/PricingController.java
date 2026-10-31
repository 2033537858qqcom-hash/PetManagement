package com.petcare.grooming.catalog;
import com.petcare.grooming.common.ApiResponse;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/services/{id}/prices")
public class PricingController {
    private final ServicePricing pricing;
    PricingController(ServicePricing pricing){this.pricing=pricing;}
    @GetMapping public ApiResponse<?> list(@PathVariable long id){return ApiResponse.ok(pricing.rules(id));}
    @PutMapping public ApiResponse<?> save(@PathVariable long id,@RequestBody ServicePricing.Rules rules){return ApiResponse.ok(pricing.save(id,rules));}
}
