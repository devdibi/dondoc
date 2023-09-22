package com.dondoc.backend.moim.entity;

import com.dondoc.backend.user.entity.Account;
import com.dondoc.backend.user.entity.User;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name="MoimMember")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class MoimMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id")
    private Long id;

    // 양방향
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="userId")
    private User user;

    // 양방향
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="moimId")
    private Moim moim;

    // 양방향
    @OneToMany(mappedBy = "moimMember",cascade = CascadeType.REMOVE)
    private List<WithdrawRequest> withdrawRequests;
    /**
     * 0 : 관리자
     * 1 : 회원
     **/
    @Column(name="userType", nullable = false)
    private int userType;

    /**
     * 0 : 승인 대기중
     * 1 : 승인
     **/
    @Column(name="status", nullable = false)
    private int status;

    @Column(name="invitedAt", updatable = false)
    @CreatedDate
    private LocalDateTime invitedAt;

    @Column(name="signedAt")
    @LastModifiedDate
    private LocalDateTime signedAt;

    // 단방향
    @OneToOne
    @JoinColumn(name = "accountId")
    private Account account;

    public MoimMember(int userType, int status){
        this.userType=userType;
        this.status=status;
    }
    public MoimMember(int userType, int status, LocalDateTime signedAt, Account account) {
        this.userType = userType;
        this.status = status;
        this.signedAt = signedAt;
        this.account = account;
    }

    public MoimMember(int userType, int status, LocalDateTime signedAt) {
        this.userType = userType;
        this.status = status;
        this.signedAt = signedAt;
    }

    public void setUser(User user){ // MoimMember 처음 생성할 때 User 연관관계 메서드
        this.user = user;
        user.getMoimMemberList().add(this);
    }

    public void setMoim(Moim moim){ // MoimMember 처음 생성할 때 Moim 연관관계 메서드
        this.moim = moim;
        moim.getMoimMemberList().add(this);
    }

    public void changeUserType(int userType){
        this.userType = userType;
    }
    public void changeStatus(int status){this.status = status;}

}