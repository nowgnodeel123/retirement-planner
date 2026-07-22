// asset/dividend/service/DividendService.java
package com.nowgnodeel.retirement_planner.asset.dividend.service;

import com.nowgnodeel.retirement_planner.asset.dividend.entity.Dividend;
import com.nowgnodeel.retirement_planner.asset.dividend.repository.DividendRepository;
import com.nowgnodeel.retirement_planner.asset.entity.Asset;
import com.nowgnodeel.retirement_planner.asset.entity.AssetCategory;
import com.nowgnodeel.retirement_planner.asset.repository.AssetRepository;
import com.nowgnodeel.retirement_planner.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.nowgnodeel.retirement_planner.asset.dividend.dto.DividendDtos.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DividendService {

    private final AssetRepository assetRepository;
    private final DividendRepository dividendRepository;

    /**
     * D-067: 배당은 국내주식/해외주식만 기록 가능. 다른 카테고리는 요청 자체를 거부한다
     * (계산을 시도하지 않고 명시적으로 막는다 — 13장 절대원칙과 동일한 태도).
     */
    @Transactional
    public DividendResponse create(Long userId, Long assetId, DividendCreateRequest request) {
        Asset asset = assetRepository.findByIdAndAccount_User_Id(assetId, userId)
                .orElseThrow(() -> new NotFoundException("자산을 찾을 수 없습니다."));

        if (asset.getCategory() != AssetCategory.DOMESTIC_STOCK
                && asset.getCategory() != AssetCategory.FOREIGN_STOCK) {
            throw new IllegalArgumentException("배당은 국내주식/해외주식 자산에만 기록할 수 있습니다.");
        }

        if (asset.getCategory() == AssetCategory.FOREIGN_STOCK && request.fx() == null) {
            throw new IllegalArgumentException("해외주식은 환율(fx) 값이 필요합니다.");
        }

        Dividend dividend = Dividend.builder()
                .asset(asset)
                .payDate(request.payDate())
                .amount(request.amount())
                .fx(asset.getCategory() == AssetCategory.FOREIGN_STOCK ? request.fx() : null)
                .build();
        dividendRepository.save(dividend);

        return toResponse(dividend);
    }

    public List<DividendResponse> findByAsset(Long userId, Long assetId) {
        assetRepository.findByIdAndAccount_User_Id(assetId, userId)
                .orElseThrow(() -> new NotFoundException("자산을 찾을 수 없습니다."));

        return dividendRepository.findAllByAssetIdOrderByPayDateDescIdDesc(assetId).stream()
                .map(this::toResponse)
                .toList();
    }

    // D-056: 배당도 삭제 확인 대상. 소유자 검증은 리포지토리 쿼리 한 번으로 강제(8.1).
    @Transactional
    public void delete(Long userId, Long assetId, Long dividendId) {
        Dividend dividend = dividendRepository
                .findByIdAndAssetIdAndAsset_Account_User_Id(dividendId, assetId, userId)
                .orElseThrow(() -> new NotFoundException("배당 기록을 찾을 수 없습니다."));
        dividendRepository.delete(dividend);
    }

    private DividendResponse toResponse(Dividend d) {
        return new DividendResponse(
                d.getId(), d.getAsset().getId(), d.getPayDate(), d.getAmount(), d.getFx()
        );
    }
}
