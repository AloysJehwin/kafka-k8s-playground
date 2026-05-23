package com.eventflow.tenant.api;

import com.eventflow.tenant.domain.Tenant;
import com.eventflow.tenant.domain.TenantPlan;
import com.eventflow.tenant.domain.TenantStatus;
import com.eventflow.tenant.infrastructure.TenantRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/tenants")
@Validated
public class TenantController {

    private final TenantRepository repository;

    public TenantController(TenantRepository repository) {
        this.repository = repository;
    }

    record CreateTenantRequest(@NotBlank String slug, @NotBlank String name, TenantPlan plan) {}

    record TenantResponse(UUID id, String slug, String name, String plan, String status, String apiKey, Instant createdAt) {
        static TenantResponse from(Tenant t) {
            return new TenantResponse(
                t.getId(),
                t.getSlug(),
                t.getName(),
                t.getPlan().name(),
                t.getStatus().name(),
                t.getApiKey(),
                t.getCreatedAt()
            );
        }
    }

    @PostMapping
    public ResponseEntity<TenantResponse> create(
            @Valid @RequestBody CreateTenantRequest req,
            UriComponentsBuilder ucb) {

        if (repository.findBySlug(req.slug()).isPresent()) {
            throw new ResponseStatusException(CONFLICT, "Slug already in use: " + req.slug());
        }

        TenantPlan plan = req.plan() != null ? req.plan() : TenantPlan.FREE;
        Tenant tenant = new Tenant(UUID.randomUUID(), req.slug(), req.name(), plan);
        repository.save(tenant);

        URI location = ucb.path("/api/tenants/{id}").buildAndExpand(tenant.getId()).toUri();
        return ResponseEntity.created(location).body(TenantResponse.from(tenant));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TenantResponse> getById(@PathVariable UUID id) {
        return repository.findById(id)
            .map(t -> ResponseEntity.ok(TenantResponse.from(t)))
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Tenant not found: " + id));
    }

    @GetMapping
    public List<TenantResponse> listActive() {
        return repository.findByStatus(TenantStatus.ACTIVE)
            .stream()
            .map(TenantResponse::from)
            .toList();
    }

    @GetMapping("/by-slug/{slug}")
    public ResponseEntity<TenantResponse> getBySlug(@PathVariable String slug) {
        return repository.findBySlug(slug)
            .map(t -> ResponseEntity.ok(TenantResponse.from(t)))
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Tenant not found for slug: " + slug));
    }

    @GetMapping("/by-api-key/{apiKey}")
    public ResponseEntity<TenantResponse> getByApiKey(@PathVariable String apiKey) {
        return repository.findByApiKey(apiKey)
            .filter(t -> t.getStatus() == TenantStatus.ACTIVE)
            .map(t -> ResponseEntity.ok(TenantResponse.from(t)))
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "No active tenant for key"));
    }

    @PatchMapping("/{id}/suspend")
    public ResponseEntity<TenantResponse> suspend(@PathVariable UUID id) {
        Tenant tenant = repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Tenant not found: " + id));
        tenant.suspend();
        repository.save(tenant);
        return ResponseEntity.ok(TenantResponse.from(tenant));
    }

    @PatchMapping("/{id}/reactivate")
    public ResponseEntity<TenantResponse> reactivate(@PathVariable UUID id) {
        Tenant tenant = repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Tenant not found: " + id));
        tenant.reactivate();
        repository.save(tenant);
        return ResponseEntity.ok(TenantResponse.from(tenant));
    }
}
