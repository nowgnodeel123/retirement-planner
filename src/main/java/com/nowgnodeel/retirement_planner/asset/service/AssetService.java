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
    private final ExchangeRateService exchangeRateService;

    @Transactional
    public HoldingResponse buy(Long userId, BuyRequest request) {
        Account account = accountRepository.findByIdAndUserId(request.accountId(), userId)
                .orElseThrow(() -> new NotFoundException("계좌를 찾을 수 없습니다."));

        if (request.category() == AssetCategory.FOREIGN_STOCK && request.fx() == null) {
            throw new IllegalArgumentException("해외주식은 환율(fx) 값이 필요합니다.");
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

    /**
     * M6: 매도 거래 등록.
     * D-057: 보유 수량(BUY 누적 - SELL 누적)을 초과하는 매도는 차단한다.
     * assetId만으로 소유자 검증까지 하므로(findByIdAndAccount_User_Id) accountId는 요청에 없다.
     */
    @Transactional
    public HoldingResponse sell(Long userId, SellRequest request) {
        Asset asset = assetRepository.findByIdAndAccount_User_Id(request.assetId(), userId)
                .orElseThrow(() -> new NotFoundException("자산을 찾을 수 없습니다."));

        if (asset.getCategory() == AssetCategory.FOREIGN_STOCK && request.fx() == null) {
            throw new IllegalArgumentException("해외주식은 환율(fx) 값이 필요합니다.");
        }

        BigDecimal currentQuantity = calculateNetQuantity(asset);
        if (request.quantity().compareTo(currentQuantity) > 0) {
            throw new IllegalArgumentException(
                    "보유 수량(" + currentQuantity + ")보다 많은 수량은 매도할 수 없습니다."); // D-057
        }

        Transaction tx = Transaction.builder()
                .asset(asset)
                .type(TransactionType.SELL)
                .tradeDate(request.tradeDate())
                .quantity(request.quantity())
                .unitPrice(request.unitPrice())
                .fx(asset.getCategory() == AssetCategory.FOREIGN_STOCK ? request.fx() : null)
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

    /**
     * M6: 자산별 거래내역(매수/매도) 조회. 최신순(desc) — 평단 계산용 asc 리포지토리 메서드와 별개.
     */
    public List<TransactionResponse> findTransactionsByAsset(Long userId, Long assetId) {
        Asset asset = assetRepository.findByIdAndAccount_User_Id(assetId, userId)
                .orElseThrow(() -> new NotFoundException("자산을 찾을 수 없습니다."));

        return transactionRepository.findAllByAssetIdOrderByTradeDateDesc(asset.getId()).stream()
                .map(tx -> new TransactionResponse(
                        tx.getId(),
                        tx.getType().name(),
                        tx.getTradeDate(),
                        tx.getQuantity(),
                        tx.getUnitPrice(),
                        tx.getQuantity().multiply(tx.getUnitPrice()),
                        tx.getFx()
                ))
                .toList();
    }

    /**
     * D-057 검증 전용으로 분리한 이유: toHoldingResponse()까지 끌고 오면 매도 저장
     * 트랜잭션 안에서 불필요한 외부 API 호출(PriceService/ExchangeRateService)이 한 번 더 발생한다.
     * 여기는 저장 "전" 시점의 순보유수량만 필요하다.
     */
    private BigDecimal calculateNetQuantity(Asset asset) {
        List<Transaction> txs = transactionRepository.findAllByAssetIdOrderByTradeDateAsc(asset.getId());
        BigDecimal buyQty = BigDecimal.ZERO;
        BigDecimal sellQty = BigDecimal.ZERO;
        for (Transaction tx : txs) {
            if (tx.getType() == TransactionType.BUY) {
                buyQty = buyQty.add(tx.getQuantity());
            } else {
                sellQty = sellQty.add(tx.getQuantity());
            }
        }
        return buyQty.subtract(sellQty);
    }

    // D-050: 파생값 계산 + M4: 현재가/평가금액/손익률 + M5: 해외주식 원화환산(D-063)
    // M6 수정: quantity가 이제 BUY 누적만이 아니라 BUY-SELL 순보유량이다.
    // (기존 버그 수정 — SELL 트랜잭션이 저장돼도 보유수량에 전혀 반영되지 않던 문제)
    private HoldingResponse toHoldingResponse(Asset asset) {
        List<Transaction> txs = transactionRepository.findAllByAssetIdOrderByTradeDateAsc(asset.getId());

        BigDecimal buyQty = BigDecimal.ZERO;
        BigDecimal buyAmount = BigDecimal.ZERO;
        BigDecimal sellQty = BigDecimal.ZERO;
        for (Transaction tx : txs) {
            if (tx.getType() == TransactionType.BUY) {
                buyQty = buyQty.add(tx.getQuantity());
                buyAmount = buyAmount.add(tx.getQuantity().multiply(tx.getUnitPrice()));
            } else {
                sellQty = sellQty.add(tx.getQuantity());
            }
        }

        // MVP 단순화(신규 결정 아님 — D-050 파생값 원칙의 연장): 평균단가는 이동평균법으로
        // 매도 시 재계산하지 않고, 전체 매수 내역 기준 평단을 그대로 유지한다.
        // 정교한 이동평균/FIFO 평단 재계산이 필요해지면(세금 탭 실현손익 정확도, M11) 별도 검토.
        BigDecimal avgPrice = buyQty.compareTo(BigDecimal.ZERO) > 0
                ? buyAmount.divide(buyQty, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal quantity = buyQty.subtract(sellQty);
        BigDecimal costBasis = avgPrice.multiply(quantity);

        BigDecimal currentPrice = null;
        BigDecimal evaluationAmount = null;
        BigDecimal profitAmount = null;
        BigDecimal profitRate = null;

        if (quantity.compareTo(BigDecimal.ZERO) > 0) {
            Optional<BigDecimal> price = priceService.getCurrentPrice(asset.getCategory(), asset.getSymbol());
            if (price.isPresent()) {
                currentPrice = price.get();
                evaluationAmount = currentPrice.multiply(quantity);
                profitAmount = evaluationAmount.subtract(costBasis);
                profitRate = costBasis.compareTo(BigDecimal.ZERO) > 0
                        ? profitAmount.divide(costBasis, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                        : BigDecimal.ZERO;
            }
        }

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
                asset.getCategory().name(), asset.getCurrency(), quantity, avgPrice,
                currentPrice, evaluationAmount, profitAmount, profitRate,
                exchangeRate, krwEvaluationAmount, exchangeRateBaseDate
        );
    }

    private String resolveCurrency(BuyRequest request) {
        return switch (request.category()) {
            case DOMESTIC_STOCK, CASH, CRYPTO -> "KRW";
            case FOREIGN_STOCK -> request.currency() != null ? request.currency() : "USD";
            default -> "KRW";
        };
    }
}
