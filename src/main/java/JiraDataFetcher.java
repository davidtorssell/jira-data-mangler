import com.atlassian.jira.rest.client.api.AuthenticationHandler;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.SearchRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.api.domain.Version;
import com.atlassian.jira.rest.client.auth.BasicHttpAuthenticationHandler;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import com.google.common.collect.Iterables;
import io.atlassian.util.concurrent.Promise;
import org.joda.time.DateTime;
import org.joda.time.Days;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ExecutionException;

public class JiraDataFetcher {

    static JiraRestClient restClient;


    public JiraDataFetcher(String jiraServer, String user, String pass) {

        URI jiraServerUri = URI.create(jiraServer);
        AsynchronousJiraRestClientFactory factory = new AsynchronousJiraRestClientFactory();
        AuthenticationHandler auth = new BasicHttpAuthenticationHandler(user, pass);
        restClient = factory.create(jiraServerUri, auth);
    }


    public long getProjectId(String projectKey) {
        String jql = "project=" + projectKey;
        Iterable<Issue> issues = jqlQuery(jql, 1);
        return Iterables.get(issues, 0).getProject().getId();
    }


    public Iterable<Version> getFixVersions(String project) {

        long projectId = getProjectId(project);
        Iterable<Version> versions = null;

        try {
            versions = restClient.getProjectClient().getProject(new URI("https://jira.extendaretail.com/rest/api/2/project/" + projectId)).get().getVersions();
            System.out.println(versions);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        for (Iterator<Version> iterator = versions.iterator(); iterator.hasNext();) {
            Version version = iterator.next();
            if (version.getReleaseDate() == null) {
                continue;
            } else if (olderThanDays(version.getReleaseDate().toDate(), 180)) {
                iterator.remove();
            }
        }
        return versions;
    }

    private Iterable<Issue> jqlQuery(String jql, int maxPerQuery) {

        SearchResult results = null;
        int startIndex = 0;
        SearchRestClient searchRestClient = restClient.getSearchClient();

        Promise<SearchResult> searchResult = searchRestClient.searchJql(jql, maxPerQuery, startIndex, null);
        results = searchResult.claim();

        return results.getIssues();
    }

    private Iterable<Issue> jqlQuery(String jql) {
        return jqlQuery(jql, 1000);
    }


    public void issuesForReleases(String project) {
        Iterable<Version> fixVersions = getFixVersions(project);

        deleteProjectDataFile(project);
        writeLineToCsv(project, "Issues," + project + " stories," + project + " bugs," + project + " other issues");

        for (Version fixVersion: fixVersions) {
            int bugs, tasks, stories, improvements, epics, tests, testExecutions, subTasks, other;
            bugs = tasks = stories = improvements = epics = tests = testExecutions = subTasks = other = 0;

            System.out.println(fixVersion.getName());
            Iterable<Issue> issues = jqlQuery("fixVersion=\"" + fixVersion.getName() + "\" AND created > -180d");
            if (versionShouldBeFiltered(fixVersion.getName())) {
                System.out.println("skipping ...");
                continue;
            }

            for (Issue issue : issues) {
                if (issue.getIssueType().getName().equalsIgnoreCase("Bug")) {
                    bugs++;
                } else if (issue.getIssueType().getName().equalsIgnoreCase("task")) {
                    stories++;
                } else if (issue.getIssueType().getName().equalsIgnoreCase("story")) {
                    stories++;
                } else if (issue.getIssueType().getName().equalsIgnoreCase("improvement")) {
                    stories++;
                } else if (issue.getIssueType().getName().equalsIgnoreCase("New feature")) {
                    stories ++;
                } else if (issue.getIssueType().getName().equalsIgnoreCase("Change")) {
                    stories ++;
                } else if (issue.getIssueType().getName().contains("Test") || issue.getIssueType().getName().contains("Tidrapportering")) {
                    //nothing
                } else {
                    //Sub-tasks, Epics
  //                  System.out.println("issue: " + issue.getKey() +  " " + issue.getIssueType().getName());
                    other ++;
                }
            }
            System.out.println("bugs: " + bugs);
            System.out.println("stories: " + stories);
            System.out.println("other: " + other);

            if (bugs > 0 && stories > 0) {
                writeLineToCsv(project, fixVersion.getName() + "," + stories + "," + bugs + "," + other);
            }
        }
    }

    private boolean versionShouldBeFiltered(String fixVersion) {
        String[] versionFilter = {"Outbox", "Inbox", "eneral", "acklog"};
        return Arrays.stream(versionFilter).parallel().anyMatch(fixVersion::contains);
    }

    private void deleteProjectDataFile(String project) {
        File file = new File("src/main/resources/" + project + ".csv");
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeLineToCsv(String filename, String line) {
        File file = new File("src/main/resources/" + filename + ".csv");
        try {
            FileWriter csvWriter = new FileWriter(file, true);
            csvWriter.append(line);
            csvWriter.append("\n");

            csvWriter.flush();
            csvWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
    private static boolean olderThanDays(Date givenDate, int days) {
        return Days.daysBetween(new DateTime(givenDate), new DateTime()).isGreaterThan(Days.days(days));
    }
}