// asset/service/AccountService.java
package com.nowgnodeel.retirement_planner.asset.service;

import com.nowgnodeel.retirement_planner.asset.entity.Account;
import com.nowgnodeel.retirement_planner.asset.repository.AccountRepository;
import com.nowgnodeel.retirement_planner.common.audit.AuditAction;
import com.nowgnodeel.retirement_planner.common.audit.AuditLogging;
import com.nowgnodeel.retirement_planner.common.exception.NotFoundException;
import com.nowgnodeel.retirement_planner.user.entity.User;
import com.nowgnodeel.retirement_planner.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.nowgnodeel.retirement_planner.asset.dto.AccountDtos.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    @Transactional
    @AuditLogging(action = AuditAction.CREATE, entityType = "Account")
    public Response create(Long userId, CreateRequest request) {
        // getReferenceById(지연 프록시)를 쓰면 사용자가 실제로 있는지 확인하지 않고 INSERT까지
        // 간다. 탈퇴한 사용자의 액세스 토큰(최대 15분 생존)으로 계좌를 만들면 그때서야 FK
        // 제약에 걸려 500 "일시적인 문제가 발생했어요"가 나갔다 — 서버 잘못이 아닌데
        // 에러 로그에 스택트레이스까지 남는다. 실제 원인은 "그 사용자가 없다"이므로 404가 맞다.
        // 계좌 생성은 드문 쓰기라 조회 한 번 더 하는 비용은 무시할 만하다.
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));

        Account account = Account.builder()
                .user(user)
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
    @AuditLogging(action = AuditAction.UPDATE, entityType = "Account")
    public Response rename(Long userId, Long accountId, RenameRequest request) {
        Account account = accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new NotFoundException("계좌를 찾을 수 없습니다."));
        account.rename(request.name());
        return Response.from(account);
    }

    /**
     * 사용자가 끌어서 정한 계좌 순서 저장. 화면에 보이는 순서 그대로의 id 목록을 받아
     * 0부터 다시 번호를 매긴다(중간 값을 비워두는 sparse 방식은 재정렬이 반복되면 결국
     * 재계산이 필요해져서, 목록 크기가 작은 이 화면에선 전체 재부여가 더 단순하다).
     * 요청에 담긴 id가 하나라도 내 계좌가 아니면 전체를 거부한다 — 부분 적용은 남의 계좌
     * 존재 여부를 알려주는 단서가 된다.
     */
    @Transactional
    @AuditLogging(action = AuditAction.UPDATE, entityType = "Account(order)")
    public void reorder(Long userId, ReorderRequest request) {
        Map<Long, Account> owned = accountRepository.findAllByUserId(userId).stream()
                .collect(Collectors.toMap(Account::getId, a -> a));

        List<Long> ids = request.orderedIds();
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new IllegalArgumentException("계좌 순서에 중복된 항목이 있습니다.");
        }
        for (Long id : ids) {
            if (!owned.containsKey(id)) {
                throw new NotFoundException("계좌를 찾을 수 없습니다.");
            }
        }

        for (int i = 0; i < ids.size(); i++) {
            owned.get(ids.get(i)).updateSortOrder(i);
        }
    }

    @Transactional
    @AuditLogging(action = AuditAction.DELETE, entityType = "Account")
    public void delete(Long userId, Long accountId) {
        Account account = accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new NotFoundException("계좌를 찾을 수 없습니다."));
        accountRepository.delete(account);
        // 실행취소 토스트(D-056)는 프론트에서 낙관적 UI로 처리 — 서버는 즉시 hard delete
    }
}