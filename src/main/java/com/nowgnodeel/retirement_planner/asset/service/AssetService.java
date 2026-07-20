// asset/service/AssetService.java
package com.nowgnodeel.retirement_planner.asset.service;

import com.nowgnodeel.retirement_planner.asset.entity.*;
import com.nowgnodeel.retirement_planner.asset.fx.entity.ExchangeRate;
import com.nowgnodeel.retirement_planner.asset.fx.service.ExchangeRateService;
import com.nowgnodeel.retirement_planner.asset.price.PriceService;
import com.nowgnodeel.retirement_planner.asset.repository.*;
import com.nowgnodeel.retirement_planner.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

import static com.nowgnodeel.retirement_planner.asset.dto.AssetDtos.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetService {

    private final AssetRepository assetRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final PriceService priceService;
    private final ExchangeRateService exchangeRateService; // M5: 해외주식 원화환산(D-063)

    @Transactional
    public HoldingResponse buy(Long userId, BuyRequest request) {
        Account account = accountRepository.findByIdAndUserId(request.accountId(), userId)
                .orElseThrow(() -> new NotFoundException("계좌를 찾을 수 없습니다."));

        if (request.category() == AssetCategory.FOREIGN_STOCK && request.fx() == null) {
            throw new IllegalArgumentException("해외주식은 환율(fx) 값이 필요합니다."); // D-063
        }

        Asset asset = assetRepository.findByAccountIdAndSymbol(account.getId(), request.symbol())
                .orElseGet(() -> assetRepository.save(
                        Asset.builder()
                                .account(account)
                                .category(request.category())
                                .name(request.name())
                                .symbol(request.symbol())
                                .currency(resolveCurrency(request))
                                .build()
                ));

        Transaction tx = Transaction.builder()
                .asset(asset)
                .type(TransactionType.BUY)
                .tradeDate(request.tradeDate())
                .quantity(request.quantity())
                .unitPrice(request.unitPrice())
                .fx(request.category() == AssetCategory.FOREIGN_STOCK ? request.fx() : null)
                .build();
        transactionRepository.save(tx);

        return toHoldingResponse(asset);
    }

    public List<HoldingResponse> findHoldingsByAccount(Long userId, Long accountId) {
        accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new NotFoundException("계좌를 찾을 수 없습니다."));

        return assetRepository.findAllByAccountId(accountId).stream()
                .map(this::toHoldingResponse)
                .toList();
    }

    // D-050: 파생값 계산 + M4: 현재가/평가금액/손익률 반영 + M5: 해외주식 원화환산(D-063)
    private HoldingResponse toHoldingResponse(Asset asset) {
        List<Transaction> txs = transactionRepository.findAllByAssetIdOrderByTradeDateAsc(asset.getId());

        BigDecimal buyQty = BigDecimal.ZERO;
        BigDecimal buyAmount = BigDecimal.ZERO;
        for (Transaction tx : txs) {
            if (tx.getType() == TransactionType.BUY) {
                buyQty = buyQty.add(tx.getQuantity());
                buyAmount = buyAmount.add(tx.getQuantity().multiply(tx.getUnitPrice()));
            }
        }
        BigDecimal avgPrice = buyQty.compareTo(BigDecimal.ZERO) > 0
                ? buyAmount.divide(buyQty, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal currentPrice = null;
        BigDecimal evaluationAmount = null;
        BigDecimal profitAmount = null;
        BigDecimal profitRate = null;

        if (buyQty.compareTo(BigDecimal.ZERO) > 0) {
            Optional<BigDecimal> price = priceService.getCurrentPrice(asset.getCategory(), asset.getSymbol());
            if (price.isPresent()) {
                currentPrice = price.get();
                evaluationAmount = currentPrice.multiply(buyQty);
                profitAmount = evaluationAmount.subtract(buyAmount);
                profitRate = buyAmount.compareTo(BigDecimal.ZERO) > 0
                        ? profitAmount.divide(buyAmount, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                        : BigDecimal.ZERO;
            }
        }

        // M5: 해외주식만 원화 이중표시(D-063). 환율 캐시가 없으면 그냥 null로 남기고 정상 렌더(D-058과 동일한 degrade 원칙).
        BigDecimal exchangeRate = null;
        BigDecimal krwEvaluationAmount = null;
        String exchangeRateBaseDate = null;

        if (asset.getCategory() == AssetCategory.FOREIGN_STOCK && evaluationAmount != null) {
            Optional<ExchangeRate> rate = exchangeRateService.getRate("USD");
            if (rate.isPresent()) {
                exchangeRate = rate.get().getDealBasR();
                krwEvaluationAmount = evaluationAmount.multiply(exchangeRate);
                exchangeRateBaseDate = rate.get().getBaseDate().toString();
            }
        }

        return new HoldingResponse(
                asset.getId(), asset.getSymbol(), asset.getName(),
                asset.getCategory().name(), asset.getCurrency(), buyQty, avgPrice,
                currentPrice, evaluationAmount, profitAmount, profitRate,
                exchangeRate, krwEvaluationAmount, exchangeRateBaseDate
        );
    }

    // 가정: 크립토는 업비트 원화마켓 기준 KRW, 해외주식만 USD(또는 요청값). 국내주식/현금은 KRW 고정.
    private String resolveCurrency(BuyRequest request) {
        return switch (request.category()) {
            case DOMESTIC_STOCK, CASH, CRYPTO -> "KRW";
            case FOREIGN_STOCK -> request.currency() != null ? request.currency() : "USD";
            default -> "KRW";
        };
    }
}