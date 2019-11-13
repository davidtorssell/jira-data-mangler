/**
 * Get all issues based on the search filter
 */
public class Main {

    private static JiraDataFetcher jira;

    public static void main(String[] args) throws Exception {

        jira = new JiraDataFetcher("https://jira.extendaretail.com", "david.torssell@extendaretail.com", "Extenda2019");

        jira.issuesForReleases("PSREX");
        jira.issuesForReleases("PSO");
        jira.issuesForReleases("PSC");
        jira.issuesForReleases("PSDOS");
    }
}