package me.escoffier.timeless.inboxes.google;

import com.google.api.services.drive.model.*;
import me.escoffier.timeless.model.Backend;
import me.escoffier.timeless.model.Inbox;
import me.escoffier.timeless.model.NewTaskRequest;
import me.escoffier.timeless.model.Task;
import org.jboss.logging.Logger;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.util.*;

@ApplicationScoped
public class GoogleDriveService implements Inbox {


    private List<UnresolvedComment> fetched;

    @Inject GoogleAccounts accounts;
    @Inject Logger logger;

    @Override
    public List<Runnable> getPlan(Backend backend) {
        if (fetched == null) {
            fetch();
        }

        List<Task> existingDriveTasks = backend.getMatchingTasks(this::isUnresolvedComment);

        // 1 - If fetched contains a comment without an associated task -> create new task
        // 2 - If fetched contains a comment with an uncompleted associated task -> do nothing
        // 3 - If backend contains "comment" tasks (existingDriveTasks) without an associated comment in fetched -> complete task

        List<Runnable> actions = new ArrayList<>();
        for (UnresolvedComment comment : fetched) {
            NewTaskRequest request = comment.asNewTaskRequest();
            Optional<Task> maybe = backend.getTaskMatchingRequest(request);
            if (maybe.isEmpty()) {
                // Case 1
                actions.add(() -> backend.create(request));
            }
        }
        
        for (Task task : existingDriveTasks) {
            Optional<UnresolvedComment> thread = fetched.stream().filter(s -> s.content().equalsIgnoreCase(task.content)).findFirst();
            if (thread.isEmpty()) {
                // 4 - complete the task
                actions.add(() -> backend.complete(task));
            }
        }

        return actions;
    }

    private boolean isUnresolvedComment(Task task) {
        return task.content.contains("](https://docs.google.com");
    }

    private List<com.google.api.services.drive.model.File> getDocuments(Account account) {
        try {
            FileList result = account.drive().files().list()
                    .setPageSize(100)
                    .setQ("fullText contains 'followup:actionitems'")
                    .setFields("nextPageToken, files(id, name, webViewLink)")
                    .execute();

            List<File> files = result.getFiles();

            if (files == null) {
                return Collections.emptyList();
            }
            return files;
        } catch (IOException e) {
            logger.error("Unable to retrieve Google drive document for user " + account.name());
            return Collections.emptyList();
        }
    }

    private List<Comment> getComments(Account account, File document) {
        try {
            CommentList list = account.drive().comments().list(document.getId())
                    .setFields("*")
                    .setPageSize(20)
                    .execute();
            if (list == null) {
                return Collections.emptyList();
            }
            return list.getComments();
        } catch (IOException e) {
            logger.errorf("Unable to retrieve comments from document %s (%s) for user %s.",
                    document.getName(), document.getWebViewLink(), account.name());
            return Collections.emptyList();
        }
    }

    private boolean isResolved(Comment comment) {
        return comment.getResolved() != null  && comment.getResolved();
    }

    private boolean isDeleted(Comment comment) {
        return comment.getDeleted() != null  && comment.getDeleted();
    }

    private List<UnresolvedComment> extractUnresolvedFollowUp(Account account, File file, List<Comment> comments) {
        List<UnresolvedComment> list = new ArrayList<>();
        for (Comment comment : comments) {
            if (!isResolved(comment) || !isDeleted(comment)) {
                String action = "";
                if (comment.getReplies() != null) {
                    for (Reply reply : comment.getReplies()) {
                        if (reply.getAction() != null) {
                            action = reply.getAction();
                        }
                    }
                }
                if ((comment.getContent().contains("+" + account.email()) || comment.getContent().contains("@" + account.email()))
                        && !action.equalsIgnoreCase("resolve")) {
                    list.add(new UnresolvedComment(file, comment));
                }
            }
        }
        return list;
    }

    @PostConstruct
    public List<UnresolvedComment> fetch() {
        logger.info("\uD83D\uDEB6  Retrieving follow-up tasks from Google Drive...");
        Set<UnresolvedComment> comments = new HashSet<>();
        try {
            for (Account account : accounts.accounts().values()) {
                for (File document : getDocuments(account)) {
                    List<Comment> list = getComments(account, document);
                    comments.addAll(extractUnresolvedFollowUp(account, document, list));
                }
            }

            fetched = new ArrayList<>(comments);
            logger.infof("\uD83D\uDEB6  %d unresolved comments from Google Drive retrieved", fetched.size());
            return fetched;
        } catch (Exception e) {
            throw new IllegalStateException("\uD83D\uDC7F Unable to retrieve messages from Google Drive", e);
        }
    }

}
