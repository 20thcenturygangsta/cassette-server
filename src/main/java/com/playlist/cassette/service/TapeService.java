package com.playlist.cassette.service;

import com.playlist.cassette.dto.tape.TapeListResponseDto;
import com.playlist.cassette.dto.tape.TapeResponseDto;
import com.playlist.cassette.dto.tape.TapeSaveRequestDto;
import com.playlist.cassette.dto.tape.TapeUpdateRequestDto;
import com.playlist.cassette.entity.Member;
import com.playlist.cassette.entity.Tape;
import com.playlist.cassette.handler.exception.ExceptionCode;
import com.playlist.cassette.handler.exception.UserException;
import com.playlist.cassette.repository.MemberRepository;
import com.playlist.cassette.repository.TapeRepository;
import com.playlist.cassette.repository.TrackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class TapeService {

    private final TapeRepository tapeRepository;
    private final MemberRepository memberRepository;
    private final TrackRepository trackRepository;

    public List<TapeListResponseDto> getTapes(Long memberId) {
        Member member = memberRepository.findById(memberId).orElseThrow(() ->
                new UserException(ExceptionCode.INVALID_MEMBER, ExceptionCode.INVALID_MEMBER.getMessage()));
        return tapeRepository.findTapeByMember(member).stream().map(TapeListResponseDto::new).collect(Collectors.toList());
    }

    public TapeResponseDto createTape(Long memberId, TapeSaveRequestDto requestDto) {
        Member member = memberRepository.findById(memberId).orElseThrow(() ->
                new UserException(ExceptionCode.INVALID_MEMBER, ExceptionCode.INVALID_MEMBER.getMessage()));
        Tape tape = tapeRepository.save(requestDto.toEntity(member));

        return TapeResponseDto.builder().tape(tape).build();
    }

    public TapeResponseDto updateTape(Long id, TapeUpdateRequestDto requestDto) {
        Tape tape = tapeRepository.findById(id).orElseThrow(() ->
                new UserException(ExceptionCode.NOT_FOUND_TAPES, ExceptionCode.NOT_FOUND_TAPES.getMessage()));

        tape.update(requestDto.getColorCode(), requestDto.getName());
        tapeRepository.save(tape);

        return TapeResponseDto.builder().tape(tape).build();
    }

    public ResponseEntity<byte[]> downloadTape(Long id, String dirName) throws IOException {
        //List<TrackResponseDto> track = (List<TrackResponseDto>) trackRepository.findByTapeId(id);

        return new ResponseEntity<>(HttpStatus.OK);
    }
}
