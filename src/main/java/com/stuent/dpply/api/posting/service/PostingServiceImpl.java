package com.stuent.dpply.api.posting.service;

import com.stuent.dpply.api.auth.domain.entity.User;
import com.stuent.dpply.api.auth.domain.enums.UserRole;
import com.stuent.dpply.api.posting.domain.dto.CreateCommentDto;
import com.stuent.dpply.api.posting.domain.dto.CreatePostDto;
import com.stuent.dpply.api.posting.domain.dto.ModifyCommentDto;
import com.stuent.dpply.api.posting.domain.dto.ModifyPostDto;
import com.stuent.dpply.api.posting.domain.entity.Posting;
import com.stuent.dpply.api.posting.domain.entity.PostingComment;
import com.stuent.dpply.api.posting.domain.entity.PostingSympathy;
import com.stuent.dpply.api.posting.domain.enums.PostingTag;
import com.stuent.dpply.api.posting.domain.enums.SortMethod;
import com.stuent.dpply.api.posting.domain.enums.PostingStatus;
import com.stuent.dpply.api.posting.domain.enums.PostingSympathyStatus;
import com.stuent.dpply.api.posting.domain.repository.PostingCommentRepository;
import com.stuent.dpply.api.posting.domain.repository.PostingCountRepository;
import com.stuent.dpply.api.posting.domain.repository.PostingRepository;
import com.stuent.dpply.api.posting.domain.repository.PostingSympathyRepository;
import com.stuent.dpply.api.posting.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PostingServiceImpl implements PostingService{

    private final PostingRepository postingRepository;
    private final PostingSympathyRepository postingSympathyRepository;
    private final PostingCountRepository postingCountRepository;
    private final PostingCommentRepository postingCommentRepository;

    @Override
    public List<Posting> getPostByStatusAndSort(PostingStatus status, SortMethod sort) {
        if(sort.equals(SortMethod.RECENT)) {
            return postingRepository.findByStatusOrderByCreateAt(status);
        }else {
            return postingRepository.findByStatusOrderBySympathyCount(status);
        }
    }

    @Override
    public List<Posting> getPostByPageAndLimit(int page, int limit) {
        PageRequest pageRequest = PageRequest.of(page - 1, limit);
        return postingRepository.findAll(pageRequest).getContent();
    }

    @Override
    public List<Posting> getPostByTag(PostingTag tag) {
        return postingRepository.findByTag(tag);
    }

    @Override
    public Posting getPostById(Long id) {
        return postingRepository.findById(id)
                .orElseThrow(() -> PostNotFoundException.EXCEPTION);
    }

    @Override
    public void createPost(User user, CreatePostDto dto) {
        LocalDate now = LocalDate.now();
        int postingCount = postingRepository.countByUserAndCreateAtBetween(user, now.minusMonths(1), now);

        int count = postingCountRepository.findById(1L)
                .orElseThrow(() -> PostCountNotFoundException.EXCEPTION).getCount();
        if(postingCount >= count) {
            throw DoNotPostException.EXCEPTION;
        }
        Posting posting = Posting.builder()
                .title(dto.getTitle())
                .tag(dto.getTag())
                .text(dto.getText())
                .user(user)
                .build();
        postingRepository.save(posting);
    }

    @Override
    public void modifyPost(User user, ModifyPostDto dto) {
        Posting posting = postingRepository.findById(dto.getPostingId())
                .orElseThrow(() -> PostNotFoundException.EXCEPTION);
        if(!(posting.getUser().equals(user))) {
            throw NotCtrlPostException.EXCEPTION;
        }
        posting.updatePosting(dto.getTitle(), dto.getText(), posting.getStatus(), LocalDate.now());
        postingRepository.save(posting);
    }

    @Override
    @Transactional
    public void deletePost(User user, Long id) {
        Posting posting = postingRepository.findById(id)
                .orElseThrow(() -> PostNotFoundException.EXCEPTION);
        if(!(posting.getUser().equals(user)) && !(user.getRole().equals(UserRole.ADMIN))) {
            throw NotCtrlPostException.EXCEPTION;
        }
        postingRepository.delete(posting);
    }

    @Override
    public void soledPost(Long id) {
        Posting posting = postingRepository.findById(id)
                .orElseThrow(() -> PostNotFoundException.EXCEPTION);
        posting.updatePosting(posting.getTitle(), posting.getText(), PostingStatus.SOLVED, null);
        postingRepository.save(posting);
    }

    @Override
    public void refusePost(Long id) {
        Posting posting = postingRepository.findById(id)
                .orElseThrow(() -> PostNotFoundException.EXCEPTION);
        posting.updatePosting(posting.getTitle(), posting.getText(), PostingStatus.REFUSED,null);
        postingRepository.save(posting);
    }

    @Override
    public void signSympathy(User user, Long id) {
        Posting posting = postingRepository.findById(id)
                .orElseThrow(() -> PostNotFoundException.EXCEPTION);
        PostingSympathy postingSympathy = postingSympathyRepository.findByUserAndPosting(user, posting)
                .orElseGet(() -> PostingSympathy.builder()
                        .posting(posting)
                        .user(user)
                        .build());
        postingSympathy.updateStatus(PostingSympathyStatus.YES);
        postingSympathyRepository.save(postingSympathy);
    }

    @Override
    public void cancelSympathy(User user, Long id) {
        Posting posting = postingRepository.findById(id)
                .orElseThrow(() -> PostNotFoundException.EXCEPTION);
        PostingSympathy postingSympathy = postingSympathyRepository.findByUserAndPosting(user, posting)
                .orElseThrow(() -> PostingSympathyNotFoundException.EXCEPTION);
        postingSympathy.updateStatus(PostingSympathyStatus.NO);
        postingSympathyRepository.save(postingSympathy);
    }

    @Override
    public List<PostingComment> getPostingComment(Long id) {
        Posting posting = postingRepository.findById(id)
                .orElseThrow(() -> PostNotFoundException.EXCEPTION);
        return postingCommentRepository.findByPosting(posting);
    }

    @Override
    public void createComment(User user, Long id, CreateCommentDto dto) {
        Posting posting = postingRepository.findById(id)
                .orElseThrow(() -> PostNotFoundException.EXCEPTION);
        PostingComment comment = PostingComment.builder()
                .user(user)
                .posting(posting)
                .comment(dto.getComment())
                .build();
        postingCommentRepository.save(comment);
    }

    @Override
    public void modifyComment(User user, Long id, ModifyCommentDto dto) {
        PostingComment postingComment = postingCommentRepository.findById(id)
                        .orElseThrow(() -> PostNotFoundException.EXCEPTION);
        if(!user.equals(postingComment.getUser())) {
            throw NotCtrlPostException.EXCEPTION;
        }
        postingComment.modifyComment(dto.getComment());
        postingCommentRepository.save(postingComment);
    }

    @Override
    public void deleteComment(User user, Long id) {
        PostingComment postingComment = postingCommentRepository.findById(id)
                .orElseThrow(() -> PostNotFoundException.EXCEPTION);
        if(!user.equals(postingComment.getUser())) {
            throw NotCtrlPostException.EXCEPTION;
        }
        postingCommentRepository.delete(postingComment);
    }
}
