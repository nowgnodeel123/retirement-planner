// asset/service/AccountService.java
package com.nowgnodeel.retirement_planner.asset.service;

import com.nowgnodeel.retirement_planner.asset.entity.Account;
import com.nowgnodeel.retirement_planner.asset.repository.AccountRepository;
import com.nowgnodeel.retirement_planner.common.exception.NotFoundException;
import com.nowgnodeel.retirement_planner.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.nowgnodeel.retirement_planner.asset.dto.AccountDtos.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    @Transactional
    public Response create(Long userId, CreateRequest request) {
        Account account = Account.builder()
                .user(userRepository.getReferenceById(userId))  // 프록시 참조만, 추가 쿼리 없음
                .name(request.name())
                .institutionType(request.institutionType())
                .detailType(request.detailType())
                .build();

        return Response.from(accountRepository.save(account));
    }

    public List<Response> findAllByUser(Long userId) {
        return accountRepository.findAllByUserId(userId).stream()
                .map(Response::from)
                .toList();
    }

    @Transactional
    public void delete(Long userId, Long accountId) {
        Account account = accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new NotFoundException("계좌를 찾을 수 없습니다."));
        accountRepository.delete(account);
        // 실행취소 토스트(D-056)는 프론트에서 낙관적 UI로 처리 — 서버는 즉시 hard delete
    }
}