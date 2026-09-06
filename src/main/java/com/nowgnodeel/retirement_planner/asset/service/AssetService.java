// asset/service/AssetService.java
package com.nowgnodeel.retirement_planner.asset.service;

import com.nowgnodeel.retirement_planner.asset.entity.*;
import com.nowgnodeel.retirement_planner.asset.fx.entity.ExchangeRate;
import com.nowgnodeel.retirement_planner.asset.fx.service.ExchangeRateService;
import com.nowgnodeel.retirement_planner.asset.price.PriceService;
import com.nowgnodeel.retirement_planner.asset.repository.*;
import com.nowgnodeel.retirement_planner.asset.stock.entity.DomesticStock;
import com.nowgnodeel.retirement_planner.asset.stock.repository.DomesticStockRepository;
import com.nowgnodeel.retirement_planner.common.audit.AuditAction;
import com.nowgnodeel.retirement_planner.common.audit.AuditLogging;
import com.nowgnodeel.retirement_planner.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
    private final DomesticStockRepository domesticStockRepository;

    @Transactional
    @AuditLogging(action = AuditAction.CREATE, entityType = "Transaction(BUY)")
    public HoldingResponse buy(Long userId, BuyRequest request) {
        Account account = accountRepository.findByIdAndUserId(request.accountId(), userId)
                .orElseThrow(() -> new NotFoundException("계좌를 찾을 수 없습니다."));

        if (!isCategoryAllowedForInstitution(account.getInstitutionType(), request.category())) {
            throw new IllegalArgumentException("해당 계좌 유형에서는 등록할 수 없는 자산 카테고리입니다.");
        }

        // D-198: 연금저축·IRP는 세제혜택 계좌라 "지정 상품만 거래 가능"하다.
        // 원래 의도는 개별주 차단이었는데 구현이 매수 자체를 전부 막고 있었다 —
        // 실제로 이 계좌들에서 담는 ETF(KODEX 미국나스닥100 등)는 허용해야 맞다.
        if (account.getDetailType() == AccountDetailType.IRP
                || account.getDetailType() == AccountDetailType.PENSION_SAVINGS) {
            if (request.category() != AssetCategory.DOMESTIC_STOCK
                    || !isEtf(request.symbol())) {
                throw new IllegalArgumentException(
                        "연금저축·IRP 계좌에서는 ETF만 매수할 수 있어요. 개별 종목·해외주식·암호화폐는 담을 수 없습니다.");
            }
        }

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
    @AuditLogging(action = AuditAction.CREATE, entityType = "Transaction(SELL)")
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

    /**
     * M6 후속: 잘못 입력한 매매 거래의 정정. 수량·단가·환율·거래일만 바꾼다(type은 불변).
     * D-050 그대로 — 수량·평단·손익은 저장돼 있지 않고 transactions에서 파생되므로,
     * 거래 한 건을 고치면 보유수량·평단·실현손익·세금 추정이 전부 자동으로 따라온다.
     * D-057 연장: 정정 결과 보유수량이 음수가 되면(매수를 줄였는데 이미 그만큼 매도했다면) 거부한다.
     */
    @Transactional
    @AuditLogging(action = AuditAction.UPDATE, entityType = "Transaction")
    public HoldingResponse updateTransaction(Long userId, Long assetId, Long transactionId,
                                             TransactionUpdateRequest request) {
        Transaction tx = transactionRepository
                .findByIdAndAssetIdAndAsset_Account_User_Id(transactionId, assetId, userId)
                .orElseThrow(() -> new NotFoundException("거래 내역을 찾을 수 없습니다."));

        Asset asset = tx.getAsset();
        boolean isForeignStock = asset.getCategory() == AssetCategory.FOREIGN_STOCK;
        if (isForeignStock && request.fx() == null) {
            throw new IllegalArgumentException("해외주식은 환율(fx) 값이 필요합니다.");
        }

        // 이 거래를 뺐다가 새 값으로 다시 넣었을 때의 보유수량을 미리 계산한다.
        // 실제 반영 후 재조회하면 flush 시점에 기대는 코드가 되므로 산술로 먼저 확인한다.
        BigDecimal projected = calculateNetQuantity(asset)
                .subtract(signedQuantity(tx.getType(), tx.getQuantity()))
                .add(signedQuantity(tx.getType(), request.quantity()));
        if (projected.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "이렇게 수정하면 보유 수량이 마이너스가 돼요 (" + plain(projected)
                            + "). 매도 내역을 먼저 정리해주세요."); // D-057
        }

        tx.update(request.tradeDate(), request.quantity(), request.unitPrice(),
                isForeignStock ? request.fx() : null);

        return toHoldingResponse(asset);
    }

    /** M6 후속: 잘못 등록한 매매 거래 삭제. 정정과 동일하게 보유수량 음수를 막는다. */
    @Transactional
    @AuditLogging(action = AuditAction.DELETE, entityType = "Transaction")
    public void deleteTransaction(Long userId, Long assetId, Long transactionId) {
        Transaction tx = transactionRepository
                .findByIdAndAssetIdAndAsset_Account_User_Id(transactionId, assetId, userId)
                .orElseThrow(() -> new NotFoundException("거래 내역을 찾을 수 없습니다."));

        BigDecimal projected = calculateNetQuantity(tx.getAsset())
                .subtract(signedQuantity(tx.getType(), tx.getQuantity()));
        if (projected.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "이 매수를 지우면 보유 수량이 마이너스가 돼요 (" + plain(projected)
                            + "). 매도 내역을 먼저 지워주세요."); // D-057
        }

        transactionRepository.delete(tx);
    }

    /**
     * 수량 컬럼이 scale 8이라 그대로 찍으면 "-2.00000000"이 된다 — 사용자에게 보이는 문구용 정리.
     * 문구에서는 숫자를 괄호로 빼둔다: 숫자 뒤에 조사를 붙이면 읽는 소리에 따라 이/가가 갈려서
     * ("-1이", "-0.5가") 한쪽으로 고정할 수가 없다.
     */
    private String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private BigDecimal signedQuantity(TransactionType type, BigDecimal quantity) {
        return type == TransactionType.BUY ? quantity : quantity.negate();
    }

    /** 자산 표시 이름 변경(계좌 이름 변경과 같은 성격). 종목코드는 건드리지 않는다. */
    @Transactional
    @AuditLogging(action = AuditAction.UPDATE, entityType = "Asset")
    public HoldingResponse rename(Long userId, Long assetId, AssetRenameRequest request) {
        Asset asset = assetRepository.findByIdAndAccount_User_Id(assetId, userId)
                .orElseThrow(() -> new NotFoundException("자산을 찾을 수 없습니다."));
        asset.rename(request.name());
        return toHoldingResponse(asset);
    }

    /**
     * 자산 삭제. 거래·배당·입금은 FK ON DELETE CASCADE(V2)로 DB가 함께 지운다 —
     * 되돌릴 수 없으므로 프론트에서 확인 모달을 거친다(계좌 삭제와 동일 정책, D-056).
     */
    @Transactional
    @AuditLogging(action = AuditAction.DELETE, entityType = "Asset")
    public void delete(Long userId, Long assetId) {
        Asset asset = assetRepository.findByIdAndAccount_User_Id(assetId, userId)
                .orElseThrow(() -> new NotFoundException("자산을 찾을 수 없습니다."));
        assetRepository.delete(asset);
    }

    /**
     * 사용자가 끌어서 정한 자산 순서 저장. 계좌 스코프 — 한 계좌 안에서만 의미가 있다.
     * 계좌 소유자 검증을 먼저 하고, 요청 id가 그 계좌의 자산이 아니면 전체를 거부한다.
     */
    @Transactional
    @AuditLogging(action = AuditAction.UPDATE, entityType = "Asset(order)")
    public void reorderAssets(Long userId, AssetReorderRequest request) {
        accountRepository.findByIdAndUserId(request.accountId(), userId)
                .orElseThrow(() -> new NotFoundException("계좌를 찾을 수 없습니다."));

        Map<Long, Asset> owned = assetRepository.findAllByAccountId(request.accountId()).stream()
                .collect(Collectors.toMap(Asset::getId, a -> a));

        List<Long> ids = request.orderedIds();
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new IllegalArgumentException("자산 순서에 중복된 항목이 있습니다.");
        }
        for (Long id : ids) {
            if (!owned.containsKey(id)) {
                throw new NotFoundException("자산을 찾을 수 없습니다.");
            }
        }

        for (int i = 0; i < ids.size(); i++) {
            owned.get(ids.get(i)).updateSortOrder(i);
        }
    }

    /**
     * 현금·외화 자산 등록/갱신. 거래 기반이 아니라 잔액을 그대로 저장한다.
     * 계좌당 통화별 1건만 유지 — 같은 통화를 다시 등록하면 새 자산을 만들지 않고 잔액을 덮어쓴다.
     * 기관유형으로 제한하지 않는다 — 증권사 예수금, 거래소 원화/달러 예수금 모두
     * 실제로 존재하는 잔액이다. D-198(연금저축·IRP 개별 매수 차단)은 "지정 상품만 거래 가능"이라는
     * 상품 제약이라 상품이 아닌 예수금 잔액에는 적용하지 않는다.
     */
    @Transactional
    @AuditLogging(action = AuditAction.CREATE, entityType = "Asset(CASH)")
    public HoldingResponse upsertCash(Long userId, CashRequest request) {
        Account account = accountRepository.findByIdAndUserId(request.accountId(), userId)
                .orElseThrow(() -> new NotFoundException("계좌를 찾을 수 없습니다."));

        String currency = request.currency();

        // 연금저축·IRP는 원화 계좌라 달러 예수금이 존재하지 않는다. 해외주식 직접 매수도
        // 막혀 있어(D-198) 달러가 들어올 경로 자체가 없다. 이전에는 현금·외화를 계좌 유형과
        // 무관하게 열어뒀는데, 실제로 만들 수 없는 잔액을 등록할 수 있는 상태였다.
        if ("USD".equals(currency)
                && (account.getDetailType() == AccountDetailType.IRP
                    || account.getDetailType() == AccountDetailType.PENSION_SAVINGS)) {
            throw new IllegalArgumentException("연금저축·IRP 계좌에는 달러 예수금을 등록할 수 없어요.");
        }

        Asset asset = assetRepository.findByAccountIdAndSymbol(account.getId(), currency)
                .orElseGet(() -> assetRepository.save(
                        Asset.builder()
                                .account(account)
                                .category(AssetCategory.CASH)
                                .name(cashAssetName(currency))
                                .symbol(currency)
                                .currency(currency)
                                .cash(BigDecimal.ZERO)
                                .build()
                ));

        if (asset.getCategory() != AssetCategory.CASH) {
            throw new IllegalArgumentException("같은 계좌에 동일한 심볼의 다른 자산이 이미 있습니다.");
        }

        asset.updateCashBalance(request.balance());
        return toHoldingResponse(asset);
    }

    /** 자산 상세 화면의 잔액 수정. 이력 없이 덮어쓴다. */
    @Transactional
    @AuditLogging(action = AuditAction.UPDATE, entityType = "Asset(CASH)")
    public HoldingResponse updateCashBalance(Long userId, Long assetId, CashBalanceRequest request) {
        Asset asset = assetRepository.findByIdAndAccount_User_Id(assetId, userId)
                .orElseThrow(() -> new NotFoundException("자산을 찾을 수 없습니다."));

        if (asset.getCategory() != AssetCategory.CASH) {
            throw new IllegalArgumentException("현금 자산만 잔액을 직접 수정할 수 있습니다.");
        }

        asset.updateCashBalance(request.balance());
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
     * M9: 대시보드 총자산/도넛차트 집계용 — 사용자의 모든 계좌를 넘나드는 보유자산 조회.
     * findAllByAccount_UserId 자체가 userId로 스코핑되므로 별도 소유자 검증 분기 불필요.
     */
    public List<HoldingResponse> findAllHoldingsByUser(Long userId) {
        return assetRepository.findAllByAccount_UserId(userId).stream()
                .map(this::toHoldingResponse)
                .toList();
    }

    /**
     * M6: 자산별 거래내역(매수/매도) 조회. 최신순(desc) — 평단 계산용 asc 리포지토리 메서드와 별개.
     */
    public List<TransactionResponse> findTransactionsByAsset(Long userId, Long assetId) {
        Asset asset = assetRepository.findByIdAndAccount_User_Id(assetId, userId)
                .orElseThrow(() -> new NotFoundException("자산을 찾을 수 없습니다."));

        return transactionRepository.findAllByAssetIdOrderByTradeDateDescIdDesc(asset.getId()).stream()                .map(tx -> new TransactionResponse(
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

    /** 이동평균법 재생 결과 — 어느 시점의 보유수량과 취득원가 총액. */
    private record CostBasis(BigDecimal quantity, BigDecimal cost) {
        /** 나눗셈 중간값이라 표시용(4자리)보다 넉넉하게 잡는다. 수량이 8자리까지 있어서다. */
        private static final int SCALE = 8;

        BigDecimal averagePrice() {
            return quantity.compareTo(BigDecimal.ZERO) > 0
                    ? cost.divide(quantity, SCALE, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
        }
    }

    /**
     * 이동평균법으로 거래를 시간순 재생한다. 매수는 수량과 취득원가를 더하고, 매도는 그
     * 시점 평단만큼 원가를 덜어낸다(평단 자체는 매도로 바뀌지 않는다).
     *
     * stopBefore가 주어지면 그 거래 "직전" 상태에서 멈춘다 — 특정 매도의 취득원가는
     * 그 매도 시점의 평단이어야 하고, 그 뒤의 매수는 영향을 주면 안 되기 때문이다.
     *
     * krwBasis면 각 거래에 저장된 fx로 원화 환산해 누적한다(D-104, 재조회 없음).
     * fx가 없는 건은 fallbackFx로 환산 — 해외주식은 매수·매도·정정 모두 fx를 필수로
     * 검증하므로(D-063) 실제로는 나오지 않지만, 만약 있다면 외화 금액을 원화로 오독하는
     * 것보다 매도일 환율로 근사하는 편이 피해가 작다.
     */
    private CostBasis replayMovingAverage(Asset asset, Transaction stopBefore,
                                          boolean krwBasis, BigDecimal fallbackFx) {
        List<Transaction> txs = transactionRepository.findAllByAssetIdOrderByTradeDateAscIdAsc(asset.getId());
        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        for (Transaction tx : txs) {
            if (stopBefore != null && isSameTransaction(tx, stopBefore)) {
                break;
            }
            if (tx.getType() == TransactionType.BUY) {
                BigDecimal fx = krwBasis
                        ? (tx.getFx() != null ? tx.getFx() : fallbackFx)
                        : BigDecimal.ONE;
                quantity = quantity.add(tx.getQuantity());
                cost = cost.add(tx.getQuantity().multiply(tx.getUnitPrice()).multiply(fx));
            } else {
                BigDecimal soldCost = new CostBasis(quantity, cost).averagePrice().multiply(tx.getQuantity());
                quantity = quantity.subtract(tx.getQuantity());
                cost = cost.subtract(soldCost);
            }
        }
        return new CostBasis(quantity, cost);
    }

    /** 영속화 전 엔티티는 id가 없을 수 있어 동일성 비교를 먼저 본다. */
    private boolean isSameTransaction(Transaction a, Transaction b) {
        return a == b || (a.getId() != null && a.getId().equals(b.getId()));
    }

    /**
     * 표시 통화 기준 평균단가(이동평균법). 매도는 평단을 바꾸지 않고, 매수만 바꾼다.
     *
     * 이전에는 전체 매수 내역의 총평균이었다. 매도 뒤에 다시 매수하면 이미 팔아치운
     * 수량까지 평균에 남아 평단이 실제와 어긋났다 — 10주를 100에 사고 5주를 판 뒤
     * 10주를 200에 사면 실제 보유분 평단은 166.67인데 총평균은 150을 냈다.
     *
     * M10: asset/profit 서브패키지의 실현손익 계산이 동일 평단을 재사용해야 해서 public으로 공개.
     */
    public BigDecimal calculateAveragePrice(Asset asset) {
        return replayMovingAverage(asset, null, false, null)
                .averagePrice()
                .setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * D-107 실현손익 공식의 단일 출처: 매도 1건당 (매도금액 - 취득원가), 둘 다 원화 기준.
     * asset/profit(M10)과 tax(M11)가 이 메서드를 함께 재사용해 두 화면의 실현손익 수치가
     * 갈라지지 않도록 한다(R-015).
     *
     * 취득원가는 그 매도 "시점"의 이동평균 평단이다. 총평균을 쓰던 시절에는 매도한 뒤에
     * 같은 종목을 다시 사면 이미 확정된 과거 매도의 실현손익이 소급해서 바뀌었다.
     * 한국 세법상 해외주식 양도소득 취득가액도 이동평균법이 원칙이다.
     *
     * 해외주식은 매수·매도 각각 그 거래에 저장된 fx로 환산한다 — 환차손익이 실현손익에
     * 포함되어야 하기 때문이다. 이전에는 양쪽에 매도일 fx 하나만 곱해 환차손익이 통째로
     * 빠졌고(매수 fx가 DB에 있는데도 쓰지 않아 D-104를 절반만 지킨 상태였다), 그 값이
     * D-109로 세금 탭 양도소득세 추정까지 흘러갔다.
     */
    public BigDecimal calculateRealizedProfitKrw(Transaction sellTx) {
        BigDecimal sellFx = sellTx.getFx();
        boolean krwBasis = sellFx != null;
        BigDecimal quantity = sellTx.getQuantity();

        CostBasis basisAtSell = replayMovingAverage(sellTx.getAsset(), sellTx, krwBasis, sellFx);
        BigDecimal costOfSold = basisAtSell.averagePrice().multiply(quantity);

        BigDecimal proceeds = sellTx.getUnitPrice().multiply(quantity);
        if (krwBasis) {
            proceeds = proceeds.multiply(sellFx);
        }
        return proceeds.subtract(costOfSold);
    }

    // D-050: 파생값 계산 + M4: 현재가/평가금액/손익률 + M5: 해외주식 원화환산(D-063)
    // M6 수정: quantity가 이제 BUY 누적만이 아니라 BUY-SELL 순보유량이다.
    // (기존 버그 수정 — SELL 트랜잭션이 저장돼도 보유수량에 전혀 반영되지 않던 문제)
    private HoldingResponse toHoldingResponse(Asset asset) {
        if (asset.getCategory() == AssetCategory.CASH) {
            return toCashHoldingResponse(asset);
        }

        BigDecimal quantity = calculateNetQuantity(asset);
        BigDecimal avgPrice = calculateAveragePrice(asset);
        BigDecimal costBasis = avgPrice.multiply(quantity);

        BigDecimal currentPrice = null;
        BigDecimal evaluationAmount = null;
        BigDecimal profitAmount = null;
        BigDecimal profitRate = null;
        String priceAsOf = null;

        if (quantity.compareTo(BigDecimal.ZERO) > 0) {
            Optional<PriceService.Quote> quote = priceService.getQuote(asset.getCategory(), asset.getSymbol());
            if (quote.isPresent()) {
                currentPrice = quote.get().price();
                priceAsOf = quote.get().fetchedAt().toString();
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

        // 원화 기준 평가손익: 취득원가를 "매수 시점" fx로 환산해야 환차손익이 들어온다.
        // 이전에는 집계 쪽에서 profitAmount(USD)에 오늘 환율만 곱했고, 그러면 취득원가까지
        // 오늘 환율로 환산한 셈이라 환차손익이 통째로 빠졌다 — 실현손익은 D-239에서
        // 같은 이유로 이미 고쳤는데 평가손익만 남아 있던 것이다.
        BigDecimal krwProfitAmount = null;
        BigDecimal krwProfitRate = null;
        if (asset.getCategory() == AssetCategory.FOREIGN_STOCK) {
            if (krwEvaluationAmount != null) {
                BigDecimal krwCostBasis = replayMovingAverage(asset, null, true, exchangeRate).cost();
                krwProfitAmount = krwEvaluationAmount.subtract(krwCostBasis);
                krwProfitRate = krwCostBasis.compareTo(BigDecimal.ZERO) > 0
                        ? krwProfitAmount.divide(krwCostBasis, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                        : BigDecimal.ZERO;
            }
        } else {
            // 원화 자산은 표시통화가 곧 원화라 두 값이 같다.
            krwProfitAmount = profitAmount;
            krwProfitRate = profitRate;
        }

        return new HoldingResponse(
                asset.getId(), asset.getAccount().getId(), asset.getSymbol(), asset.getName(),
                asset.getCategory().name(), asset.getCurrency(), quantity, avgPrice,
                currentPrice, evaluationAmount, profitAmount, profitRate,
                exchangeRate, krwEvaluationAmount, krwProfitAmount, krwProfitRate,
                priceAsOf, exchangeRateBaseDate, asset.getSortOrder()
        );
    }

    // 프론트 assets/new/page.tsx의 allowedCategories()와 동일한 매핑 — 백엔드에도
    // 강제해 malformed 요청으로 계좌 유형과 안 맞는 카테고리(예: 증권사 계좌에 CRYPTO)가
    // 저장되는 걸 막는다.
    private String cashAssetName(String currency) {
        return "USD".equals(currency) ? "미국 달러" : "원화 현금";
    }

    private boolean isCategoryAllowedForInstitution(InstitutionType institutionType, AssetCategory category) {
        return switch (institutionType) {
            case SECURITIES -> category == AssetCategory.DOMESTIC_STOCK || category == AssetCategory.FOREIGN_STOCK;
            case EXCHANGE -> category == AssetCategory.CRYPTO;
        };
    }

    /**
     * 현금은 거래에서 수량·평단을 파생하지 않는다 — 입력한 잔액이 곧 평가금액이다.
     * 매입환율을 받지 않으므로 손익(profitAmount/profitRate)은 항상 null로 둔다.
     * 외화는 고시 매매기준율로 원화환산만 하고, 환율 조회 실패 시 krwEvaluationAmount를
     * null로 둬서 대시보드가 "시세 미조회 자산"과 같은 규칙으로 제외하게 한다.
     */
    private HoldingResponse toCashHoldingResponse(Asset asset) {
        BigDecimal balance = asset.getCash() != null ? asset.getCash() : BigDecimal.ZERO;
        boolean isKrw = "KRW".equals(asset.getCurrency());

        BigDecimal exchangeRate = null;
        BigDecimal krwEvaluationAmount = isKrw ? balance : null;
        String exchangeRateBaseDate = null;

        if (!isKrw) {
            Optional<ExchangeRate> rate = exchangeRateService.getRate(asset.getCurrency());
            if (rate.isPresent()) {
                exchangeRate = rate.get().getDealBasR();
                krwEvaluationAmount = balance.multiply(exchangeRate);
                exchangeRateBaseDate = rate.get().getBaseDate().toString();
            }
        }

        return new HoldingResponse(
                asset.getId(), asset.getAccount().getId(), asset.getSymbol(), asset.getName(),
                asset.getCategory().name(), asset.getCurrency(), balance, null,
                null, balance, null, null,
                exchangeRate, krwEvaluationAmount, null, null,
                null, exchangeRateBaseDate, asset.getSortOrder()
        );
    }

    /** 연금저축·IRP 매수 가능 판정(D-198). 마스터에 없는 종목은 ETF로 취급하지 않는다. */
    private boolean isEtf(String symbolCode) {
        return symbolCode != null
                && domesticStockRepository.findById(symbolCode)
                .map(DomesticStock::isEtf)
                .orElse(false);
    }

    private String resolveCurrency(BuyRequest request) {
        return switch (request.category()) {
            case DOMESTIC_STOCK, CASH, CRYPTO -> "KRW";
            case FOREIGN_STOCK -> request.currency() != null ? request.currency() : "USD";
            default -> "KRW";
        };
    }
}
