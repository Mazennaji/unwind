package org.unwind.orchestrator;

import org.springframework.web.bind.annotation.*;
import org.unwind.common.FailStep;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/transfers")
public class TransferController {

    private final SagaOrchestrator orchestrator;
    private final SagaRepository sagas;

    public TransferController(SagaOrchestrator orchestrator, SagaRepository sagas) {
        this.orchestrator = orchestrator;
        this.sagas = sagas;
    }

    public record TransferRequest(
            String fromAccount,
            String toAccount,
            BigDecimal amount,
            FailStep failStep
    ) {}

    @PostMapping
    public Map<String, String> transfer(@RequestBody TransferRequest req) {
        FailStep fail = req.failStep() == null ? FailStep.NONE : req.failStep();
        String sagaId = orchestrator.startTransfer(
                req.fromAccount(), req.toAccount(), req.amount(), fail);
        return Map.of("sagaId", sagaId);
    }

    @GetMapping("/{id}")
    public SagaInstance get(@PathVariable String id) {
        return sagas.findById(id).orElse(null);
    }

    @GetMapping
    public List<SagaInstance> all() {
        return sagas.findAll();
    }
}