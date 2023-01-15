package com.playlist.cassette.service;

import com.playlist.cassette.dto.tape.TapeListResponseDto;
import com.playlist.cassette.dto.tape.TapeResponseDto;
import com.playlist.cassette.dto.tape.TapeSaveRequestDto;
import com.playlist.cassette.dto.tape.TapeUpdateRequestDto;
import com.playlist.cassette.dto.track.TrackResponseDto;
import com.playlist.cassette.entity.Member;
import com.playlist.cassette.entity.Tape;
import com.playlist.cassette.entity.Track;
import com.playlist.cassette.handler.exception.ExceptionCode;
import com.playlist.cassette.handler.exception.UserException;
import com.playlist.cassette.repository.MemberRepository;
import com.playlist.cassette.repository.TapeRepository;
import com.playlist.cassette.repository.TrackRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class TapeService {

    private final AwsS3Service awsS3Service;
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

    public TapeResponseDto updateTape(Long tapeId, TapeUpdateRequestDto requestDto) {
        Tape tape = tapeRepository.findById(tapeId).orElseThrow(() ->
                new UserException(ExceptionCode.NOT_FOUND_TAPES, ExceptionCode.NOT_FOUND_TAPES.getMessage()));

        tape.update(requestDto.getColorCode(), requestDto.getName());
        tapeRepository.save(tape);

        return TapeResponseDto.builder().tape(tape).build();
    }

    public ResponseEntity<byte[]> downloadTape(Long tapeId, String dirName) throws IOException {
        Tape tape = tapeRepository.findById(tapeId).orElseThrow(() ->
                new UserException(ExceptionCode.NOT_FOUND_TAPES, ExceptionCode.NOT_FOUND_TAPES.getMessage()));

        if(tape.getAudioLink() == null) {
            List<TrackResponseDto> trackList = trackRepository.findTrackByTape(tape).stream().map(TrackResponseDto::new).collect(Collectors.toList());

            String folderPath = "src/main/" + tapeId + "TapeFolder";
            File folder = new File(folderPath);
            if(!folder.exists()) folder.mkdir();

            for(int i=0; i<trackList.size(); i++) {
                TrackResponseDto track = trackList.get(i);
                String fileName = folderPath + "/" + ("A".repeat(i+1)) + ".wav";
                String audioLink = track.getAudioLink();

                File trackFile = new File(fileName);
                FileUtils.copyURLToFile(new URL(audioLink), trackFile);
            }

            File uploadFile = new File(mergeTrack(folderPath, tape.getId()));
            String audioLink = awsS3Service.upload(uploadFile, dirName);

            tape.updateAudioLink(uploadFile.getName(), audioLink);
            tapeRepository.save(tape);
            awsS3Service.removeNewFile(folder);

            String fileName = dirName + "/" + tape.getFileName();
            String type = fileName.substring(fileName.lastIndexOf("."));
            String downName = URLEncoder.encode(tape.getMember().getName() + "'s Tape" + type, "UTF-8").replaceAll("\\+", "%20");;

            return awsS3Service.download(fileName, downName);
        } else {
            String fileName = dirName + "/" + tape.getFileName();
            String type = fileName.substring(fileName.lastIndexOf("."));
            String downName = URLEncoder.encode(tape.getMember().getName() + "'s Tape" + type, "UTF-8").replaceAll("\\+", "%20");;

            return awsS3Service.download(fileName, downName);
        }
    }

    private String mergeTrack(String folderPath, Long tapeId) {
        String tapeLink = folderPath + "/Tape_" + tapeId + ".wav";

        try {
            File file = new File(folderPath);
            File[] fileList = file.listFiles();
            Arrays.sort(fileList, new Comparator<File>() {
                @Override
                public int compare(File o1, File o2) {
                    return o1.getName().length() - o2.getName().length();
                }
            });

            AudioInputStream[] clip = new AudioInputStream[fileList.length];
            for(int i=0; i<fileList.length; i++) {
                clip[i] = AudioSystem.getAudioInputStream(new File(folderPath + "/" + fileList[i].getName()));
            }

            for(int i=1; i<fileList.length; i++) {
                if(i==1) {
                    AudioInputStream appendedFiles =
                            new AudioInputStream(
                                    new SequenceInputStream(clip[i-1], clip[i]),
                                    clip[i].getFormat(),clip[i-1].getFrameLength() + clip[i].getFrameLength());

                    AudioSystem.write(appendedFiles, AudioFileFormat.Type.WAVE, new File(folderPath + "/Tape_Ver_" + i + ".wav"));
                    awsS3Service.removeNewFile(fileList[i-1]);
                    awsS3Service.removeNewFile(fileList[i]);
                } else {
                    AudioInputStream saveClip = AudioSystem.getAudioInputStream(new File(folderPath + "/Tape_Ver_" + (i-1) + ".wav"));

                    AudioInputStream appendedFiles =
                            new AudioInputStream(
                                    new SequenceInputStream(saveClip, clip[i]),
                                    saveClip.getFormat(),saveClip.getFrameLength() + clip[i].getFrameLength());

                    if(i == fileList.length-1) {
                        AudioSystem.write(appendedFiles, AudioFileFormat.Type.WAVE, new File(tapeLink));
                    } else {
                        AudioSystem.write(appendedFiles, AudioFileFormat.Type.WAVE, new File(folderPath + "/Tape_Ver_" + i + ".wav"));
                    }
                    awsS3Service.removeNewFile(fileList[i]);
                    awsS3Service.removeNewFile(new File(folderPath + "/Tape_Ver_" + (i-1) + ".wav"));
                }
            }

        } catch(Exception e) {
            e.printStackTrace();
        }

        return tapeLink;
    }
}
