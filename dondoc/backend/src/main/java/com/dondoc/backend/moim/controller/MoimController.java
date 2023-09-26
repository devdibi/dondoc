package com.dondoc.backend.moim.controller;

import com.dondoc.backend.common.utils.ApiUtils;
import com.dondoc.backend.common.utils.ApiUtils.ApiResult;
import com.dondoc.backend.common.utils.EncryptionUtils;
import com.dondoc.backend.moim.dto.*;
import com.dondoc.backend.moim.entity.Mission;
import com.dondoc.backend.moim.entity.Moim;
import com.dondoc.backend.moim.entity.MoimMember;
import com.dondoc.backend.moim.entity.WithdrawRequest;
import com.dondoc.backend.moim.service.MoimMemberService;
import com.dondoc.backend.moim.service.MoimService;
import com.dondoc.backend.user.entity.Account;
import com.dondoc.backend.user.entity.User;
import com.dondoc.backend.user.service.AccountService;
import com.dondoc.backend.user.service.UserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import javax.validation.Valid;


@Api(value = "Moim", description = "모임 컨트롤러", tags = "모임 API")
@CrossOrigin(origins = {"http://localhost:5173", "http://j9d108.p.ssafy.io:9090"})
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/moim")
public class MoimController {

    private final MoimService moimService;
    private final MoimMemberService moimMemberService;
    private final UserService userService;
    private final AccountService accountService;
    
    @ApiOperation(value = "모임 생성", notes = "모임을 생성하는 API", response = ApiResult.class)
    @PostMapping("/create")
    public ApiResult createMoim(@ApiParam(value = "모임 생성에 필요한 값",required = true) @RequestBody MoimCreateDto.Request req, Authentication authentication){

        // 식별번호 생성
        String identificationNumber;
        try {
            identificationNumber = moimService.makeIdentificationNumber();
        }catch (Exception e){ // 중복된 식별번호가 생성되면 exception 발생
            return ApiUtils.error(e.getMessage(), HttpStatus.BAD_REQUEST);
        }

        // request body로 넘어온 값
        String moimName = req.getMoimName();
        String password = req.getPassword();
        String introduce = req.getIntroduce();
        int moimType = req.getMoimType();
        Long accountId = req.getAccountId();
        List<MoimCreateDto.InviteDto> manager = req.getManager();


        /**
         * 예금주 생성
         * API : /bank/owner/create
         * param : 식별번호, 모임이름
         **/
        if(!moimService.createOnwerAPI(identificationNumber,req.getMoimName())){
            return ApiUtils.error("예금주 생성에 실패했습니다.", HttpStatus.BAD_REQUEST);
        }

        /**
         * 계좌 개설
         * API : /bank/account/create
         * param : 모임이름, bankCode(108), 식별번호, 비밀번호
         **/
        Map<String,Object> createResult = moimService.createAccountAPI(moimName,108,identificationNumber,password);
        // 모임 계좌번호
        String moimAccountNumber = createResult.get("accountNumber").toString();
        // 모임 계좌 ID
        Long moimAccountId = Long.parseLong(createResult.get("accountId").toString());

        if(moimAccountNumber==null){
            return ApiUtils.error("모임 생성에 실패했습니다.",HttpStatus.BAD_REQUEST);
        }

        try {
            // 1. 현재 로그인한 User 엔티티 찾기 (token 헤더값에서 userId가져오기)
            UserDetails userDetails = (UserDetails)authentication.getPrincipal();
            User user = userService.findById(Long.parseLong(userDetails.getUsername()));
            // 2. Moim 엔티티 생성
            Moim moim = moimService.createMoim(identificationNumber, moimName, introduce, moimAccountId,moimAccountNumber, 0, moimType, manager.size());
            // 3. Account 엔티티 찾기 (reqDTO로 받은 accountId를 활용해서)
            Account account = accountService.findById(accountId);
            // 4. MoimMember 엔티티 생성 (User 엔티티, Moim 엔티티, Account 엔티티 활용)
            int cnt = moimMemberService.createMoimMember(user,moim,LocalDateTime.now(),account, manager);
            return ApiUtils.success(MoimCreateDto.Response.toDTO(moim));
        }catch (Exception e){
            return ApiUtils.error(e.getMessage(),HttpStatus.BAD_REQUEST);
        }

    }
    @ApiOperation(value = "모임 리스트", notes = "헤더의 accessToken토큰을 이용해 현재 사용자의 모임 리스트를 가져오는 API", response = ApiResult.class)
    @GetMapping("/list") // 헤더의 토큰을 이용해서 가져온 userId를 이용해서 moimList를 조회
    public ApiResult getMoimList(Authentication authentication){
        // 현재 로그인한 User 엔티티 찾기 (token 헤더값에서 userId가져오기)
        UserDetails userDetails = (UserDetails)authentication.getPrincipal();
        List<MoimListDto.Response> result = new ArrayList<>();

        try {
            String userId = userDetails.getUsername();
            log.info("현재 로그인 한 사용자의 userId : {} ", userId);
            List<Moim> moimList = moimService.getMoimList(Long.parseLong(userId));
            for(Moim m : moimList){ // 엔티티를 dto로 변환
                result.add(MoimListDto.Response.toDTO(m));
            }
            return ApiUtils.success(result);
        }catch (Exception e){
            return ApiUtils.error(e.getMessage(),HttpStatus.BAD_REQUEST);
        }

    }
    @ApiOperation(value = "모임 상세조회", notes = "Moim테이블의 id값을 입력받아 모임 상세정보를 가져오는 API", response = ApiResult.class)
    @GetMapping("/detail/{moimId}")
    public ApiResult getMoimDetail(@PathVariable("moimId")Long moimId,Authentication authentication){

        try{
            // 현재 로그인한 User 엔티티 찾기 (token 헤더값에서 userId가져오기)
            UserDetails userDetails = (UserDetails)authentication.getPrincipal();
            Long userId = Long.parseLong(userDetails.getUsername());

            MoimDetailDto.Response result = moimService.getMoimDetail(userId,moimId);
            return ApiUtils.success(result);

        }catch (Exception e){
            return ApiUtils.error(e.getMessage(),HttpStatus.BAD_REQUEST);
        }
    }

    @ApiOperation(value = "모임 초대", notes = "모임에 회원을 초대하는 API", response = ApiResult.class)
    @PostMapping("/invite")
    public ApiResult invite(@ApiParam(value = "모임 초대에 필요한 값",required = true)@RequestBody MoimInviteDto.Request req, Authentication authentication){
        Long moimId = req.getMoimId();
        List<MoimInviteDto.InviteDto> inviteList = req.getInvite();
        int cnt;
        try {
            // 현재 로그인한 User 엔티티 찾기 (token 헤더값에서 userId가져오기)
            UserDetails userDetails = (UserDetails)authentication.getPrincipal();
            Long userId = Long.parseLong(userDetails.getUsername());
            // 모임에 초대하는 사용자가 해당 모임에 존재하는지 확인하는 부분 (존재하지 않으면 Exception 발생)
            moimMemberService.findMoimMember(userId,moimId);

            Moim moim = moimService.findById(moimId);
            cnt = moimMemberService.inviteMoimMember(moim,inviteList);

        }catch (Exception e){
            return ApiUtils.error(e.getMessage(), HttpStatus.BAD_REQUEST);
        }

        return ApiUtils.success( "moimId = " + moimId + "에 " + cnt + "명 초대 성공");
    }

    @ApiOperation(value = "모임초대 수락 또는 거절", notes = "모임초대를 수락 또는 거절하는 API", response = ApiResult.class)
    @PatchMapping("/invite/check")
    public ApiResult inviteCheck(@ApiParam(value = "모임초대 수락 또는 거절에 필요한 값 (거절의 경우 accountId 입력 x)",required = true)@RequestBody MoimInviteCheck.Request req,Authentication authentication) {
        UserDetails userDetails = (UserDetails)authentication.getPrincipal();
        Long userId = Long.parseLong(userDetails.getUsername());
        Long moimId = req.getMoimId();
        Long accountId = req.getAccountId();
        Boolean accept = req.getAccept();
        try {
            MoimMember moimMember = moimMemberService.findMoimMember(userId, moimId);
            if (accept) { // 요청 수락
                moimMemberService.acceptMoimMember(moimMember.getId(),accountId,userId);
                return ApiUtils.success("요청이 수락되었습니다.");
            } else { // 요청 거절
                moimMemberService.deleteMoimMember(moimMember);
                return ApiUtils.success("요청이 거절되었습니다.");
            }
        } catch (Exception e) {
            return ApiUtils.error(e.getMessage(), HttpStatus.BAD_REQUEST);
        }

    }
    @ApiOperation(value = "모임 거래내역 전체 조회", notes = "모임의 거래내역을 전체 조회하는 API", response = ApiResult.class)
    @PostMapping("/history/list")
    public ApiResult getHistoryList(@ApiParam(value = "모임 거래내역 전체 조회에 필요한 값",required = true)@RequestBody MoimHistoryDto.ListRequest req) {

        String identificationNumber = req.getIdentificationNumber();
        String accountNumber = req.getAccountNumber();

        List<Object> historyList = moimService.getHistoryList(identificationNumber, accountNumber);

        if(historyList==null){
            return ApiUtils.error("모임의 거래내역을 불러오지 못했습니다.",HttpStatus.BAD_REQUEST);
        }

        return ApiUtils.success(historyList);
    }

    @ApiOperation(value = "모임 거래내" +
            "역 상세 조회", notes = "모임의 거래내역을 상세 조회하는 API", response = ApiResult.class)
    @PostMapping("/history/detail")
    public ApiResult getHistoryList(@ApiParam(value = "모임 거래내역 상세 조회에 필요한 값",required = true)@RequestBody MoimHistoryDto.DetailRequest req) {

        String identificationNumber = req.getIdentificationNumber();
        String accountNumber = req.getAccountNumber();
        Long historyId = req.getHistoryId();

        Object historyDetail = moimService.getHistoryDetail(identificationNumber, accountNumber, historyId);

        if(historyDetail==null){
            return ApiUtils.error("모임의 상세 거래내역을 불러오지 못했습니다.",HttpStatus.BAD_REQUEST);
        }

        return ApiUtils.success(historyDetail);
    }


    /** 관리자에게 출금 요청 */
    @ApiOperation(value = "출금 요청", notes = "관리자에게 출금 요청하는 API", response = ApiResult.class)
    @PostMapping("/withdraw_req")
    public ApiResult<?> withdrawReq(@ApiParam(value = "출금 요청에 필요한 Request Dto",required = true) @Valid @RequestBody WithdrawRequestDto.Request req,
                                    @AuthenticationPrincipal UserDetails userDetails) {
        try{
            Long userId = Long.parseLong(userDetails.getUsername());
            WithdrawRequestDto.Response result = moimService.withdrawReq(userId, req);
            return ApiUtils.success(result);
        }catch(Exception e){
            log.error(e.getMessage());
            return ApiUtils.error(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    /** 관리자에게 미션 요청 */
    @ApiOperation(value = "미션 요청", notes = "관리자에게 미션 요청하는 API", response = ApiResult.class)
    @PostMapping("/mission_req")
    public ApiResult<?> missionReq(@ApiParam(value = "미션 요청에 필요한 Request Dto",required = true) @Valid @RequestBody MissionRequestDto.Request req,
                                   @AuthenticationPrincipal UserDetails userDetails) {
        try{
            Long userId = Long.parseLong(userDetails.getUsername());
            MissionRequestDto.Response result = moimService.missionReq(userId, req);
            return ApiUtils.success(result);
        }catch(Exception e){
            log.error(e.getMessage());
            return ApiUtils.error(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    /** 요청 관리/목록 - 전체 리스트 조회 */
    @ApiOperation(value = "요청 리스트 조회", notes = "출금/미션의 요청 리스트를 조회하는 API", response = ApiResult.class)
    @PostMapping("/list_req")
    public ApiResult<?> getRequestList(@ApiParam(value = "요청 리스트 조회에 필요한 Request Dto",required = true) @Valid @RequestBody AllRequestDto.Request req,
                                       @AuthenticationPrincipal UserDetails userDetails) {
        try{
            Long userId = Long.parseLong(userDetails.getUsername());
            AllRequestDto.Response result = moimService.getRequestList(userId, req);
            return ApiUtils.success(result);
        }catch(Exception e){
            log.error(e.getMessage());
            return ApiUtils.error(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    /** 요청 상세조회 */
    @ApiOperation(value = "요청 상세조회", notes = "출금/미션의 요청을 상세 조회하는 API", response = ApiResult.class)
    @PostMapping("/detail_req")
    public ApiResult<?> getRequestDetail(@ApiParam(value = "요청 상세조회에 필요한 Request Dto",required = true) @Valid @RequestBody DetailRequestDto.Request req,
                                         @AuthenticationPrincipal UserDetails userDetails) {
        try{
            Long userId = Long.parseLong(userDetails.getUsername());
            DetailRequestDto.Response result = moimService.getRequestDetail(userId, req);
            return ApiUtils.success(result);
        }catch(Exception e){
            log.error(e.getMessage());
            return ApiUtils.error(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    /** 요청 취소하기*/
    @ApiOperation(value = "요청 취소하기", notes = "출금/미션의 요청을 취소하는 API", response = ApiResult.class)
    @PostMapping("/cancel_req")
    public ApiResult<?> cancelReq(@ApiParam(value = "요청 취소에 필요한 Request Dto",required = true) @Valid @RequestBody CancelRequestDto.Request req,
                                  @AuthenticationPrincipal UserDetails userDetails) {
        try{
            Long userId = Long.parseLong(userDetails.getUsername());
            CancelRequestDto.Response result = moimService.cancelReq(userId, req);
            return ApiUtils.success(result);
        }catch(Exception e){
            log.error(e.getMessage());
            return ApiUtils.error(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    /** 출금 요청 승인 */
    @ApiOperation(value = "출금요청 승인", notes = "관리자가 출금 요청을 승인하는 API", response = ApiResult.class)
    @PostMapping("/allow_req")
    public ApiResult<?> allowRequest(@ApiParam(value = "출금요청 승인에 필요한 Request Dto",required = true) @Valid @RequestBody AllowRequestDto.Request req,
                                     @AuthenticationPrincipal UserDetails userDetails) {
        try{
            Long userId = Long.parseLong(userDetails.getUsername());
            AllowRequestDto.Response result = moimService.allowRequest(userId, req);
            return ApiUtils.success(result);
        }catch(Exception e){
            log.error(e.getMessage());
            return ApiUtils.error(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }


    /** 출금 요청 거절 */
    @ApiOperation(value = "출금요청 거절", notes = "관리자가 출금 요청을 거절하는 API", response = ApiResult.class)
    @PostMapping("/reject_req")
    public ApiResult<?> rejectRequest(@ApiParam(value = "출금요청 거절에 필요한 Request Dto",required = true) @Valid @RequestBody RejectRequestDto.Request req,
                                      @AuthenticationPrincipal UserDetails userDetails) {
        try{
            Long userId = Long.parseLong(userDetails.getUsername());
            String result = moimService.rejectRequest(userId, req);
            return ApiUtils.success(result);
        }catch(Exception e){
            log.error(e.getMessage());
            return ApiUtils.error(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    /** 미션 요청 승인 */
    @ApiOperation(value = "미션요청 승인", notes = "관리자가 미션 요청을 승인하는 API", response = ApiResult.class)
    @PostMapping("/allow_mission")
    public ApiResult<?> allowMissionRequest(@ApiParam(value = "미션요청 승인에 필요한 Request Dto",required = true) @Valid @RequestBody AllowRequestDto.Request req,
                                            @AuthenticationPrincipal UserDetails userDetails) {
        try{
            Long userId = Long.parseLong(userDetails.getUsername());
            AllowRequestDto.Response result = moimService.allowMissionRequest(userId, req);
            return ApiUtils.success(result);
        }catch(Exception e){
            log.error(e.getMessage());
            return ApiUtils.error(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }


    /** 미션 요청 거절 */
    @ApiOperation(value = "미션요청 거절", notes = "관리자가 미션 요청을 거절하는 API", response = ApiResult.class)
    @PostMapping("/reject_mission")
    public ApiResult<?> rejectMissionRequest(@ApiParam(value = "미션요청 거절에 필요한 Request Dto",required = true) @Valid @RequestBody RejectRequestDto.Request req,
                                             @AuthenticationPrincipal UserDetails userDetails) {
        try{
            Long userId = Long.parseLong(userDetails.getUsername());
            String result = moimService.rejectMissionRequest(userId, req);
            return ApiUtils.success(result);
        }catch(Exception e){
            log.error(e.getMessage());
            return ApiUtils.error(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    /** 미션 성공 */
    @ApiOperation(value = "미션 성공", notes = "관리자가 미션성공 인증하는 API", response = ApiResult.class)
    @PostMapping("/success_mission")
    public ApiResult<?> successMission(@ApiParam(value = "미션 성공에 필요한 Request Dto",required = true) @Valid @RequestBody SuccessOrNotMissionDto.Request req,
                                       @AuthenticationPrincipal UserDetails userDetails) {
        try{
            Long userId = Long.parseLong(userDetails.getUsername());
            SuccessOrNotMissionDto.Response result = moimService.successMission(userId, req);
            return ApiUtils.success(result);
        }catch(Exception e){
            log.error(e.getMessage());
            return ApiUtils.error(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    /** 미션 실패 */
    @ApiOperation(value = "미션 실패", notes = "관리자가 미션실패 인증하는 API", response = ApiResult.class)
    @PostMapping("/fail_mission")
    public ApiResult<?> failMission(@ApiParam(value = "미션 실패에 필요한 Request Dto",required = true) @Valid @RequestBody SuccessOrNotMissionDto.Request req,
                                    @AuthenticationPrincipal UserDetails userDetails) {
        try{
            Long userId = Long.parseLong(userDetails.getUsername());
            SuccessOrNotMissionDto.Response result = moimService.failMission(userId, req);
            return ApiUtils.success(result);
        }catch(Exception e){
            log.error(e.getMessage());
            return ApiUtils.error(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    /** 미션 포기 */
    @ApiOperation(value = "미션 포기", notes = "회원이 미션 포기하는 API", response = ApiResult.class)
    @PostMapping("/quit_mission")
    public ApiResult<?> quitMission(@ApiParam(value = "미션 포기에 필요한 Request Dto",required = true) @Valid @RequestBody SuccessOrNotMissionDto.Request req,
                                    @AuthenticationPrincipal UserDetails userDetails) {
        try{
            Long userId = Long.parseLong(userDetails.getUsername());
            SuccessOrNotMissionDto.Response result = moimService.quitMission(userId, req);
            return ApiUtils.success(result);
        }catch(Exception e){
            log.error(e.getMessage());
            return ApiUtils.error(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }


    /** 나의 미션 조회 */
    @ApiOperation(value = "나의 미션 조회", notes = "내 미션 리스트를 조회하는 API", response = ApiResult.class)
    @GetMapping("/my_mission")
    public ApiResult<?> getMyMission(@AuthenticationPrincipal UserDetails userDetails) {
        try{
            Long userId = Long.parseLong(userDetails.getUsername());
            List<MissionInfoDto.Response> result = moimService.getMyMission(userId);
            return ApiUtils.success(result);
        }catch(Exception e){
            log.error(e.getMessage());
            return ApiUtils.error(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
}
