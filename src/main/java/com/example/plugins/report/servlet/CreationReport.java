package com.example.plugins.report.servlet;

import com.atlassian.jira.bc.ServiceOutcome;
import com.atlassian.jira.bc.issue.IssueService;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.bc.project.ProjectService;
import com.atlassian.jira.config.ConstantsManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.jql.builder.JqlClauseBuilder;
import com.atlassian.jira.jql.builder.JqlQueryBuilder;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.JiraImport;
import com.atlassian.query.Query;
import com.atlassian.templaterenderer.TemplateRenderer;
import com.atlassian.jira.user.util.UserManager;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

@Scanned
public class CreationReport extends HttpServlet {

    @JiraImport
    private final ProjectService projectService;
    @JiraImport
    private final SearchService searchService;
    @JiraImport
    private final TemplateRenderer templateRenderer;
    @JiraImport
    private final JiraAuthenticationContext authenticationContext;
    @JiraImport
    private final UserManager userManager;

    private static final String NEW_REPORT_TEMPLATE = "/templates/new.vm";
    private static final String GENERATED_REPORT_TEMPLATE = "/templates/report.vm";

    public CreationReport(IssueService issueService, ProjectService projectService,
                          SearchService searchService,
                          TemplateRenderer templateRenderer,
                          JiraAuthenticationContext authenticationContext,
                          ConstantsManager constantsManager,
                          UserManager userManager) {
        this.projectService = projectService;
        this.searchService = searchService;
        this.templateRenderer = templateRenderer;
        this.authenticationContext = authenticationContext;
        this.userManager = userManager;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> context = new HashMap<>();
        resp.setContentType("text/html;charset=utf-8");

        context.put("allUsers", getAllUsers());
        context.put("allProjects", getAllProjects());
        templateRenderer.render(NEW_REPORT_TEMPLATE, context, resp.getWriter());
    }

    private List<Project> getAllProjects() {
        ApplicationUser user = authenticationContext.getLoggedInUser();
        ServiceOutcome<List<Project>> allProjects = projectService.getAllProjects(user);
        return allProjects.get();
    }

    private Collection<ApplicationUser> getAllUsers() {
        return userManager.getAllApplicationUsers();
    }

    private List<Issue> getIssues(ApplicationUser user, String startDate, String endDate, Long projectId) {
        JqlClauseBuilder jqlClauseBuilder = JqlQueryBuilder.newClauseBuilder();
        Query query;

        if (projectId == null) {
            query = jqlClauseBuilder.createdBetween(startDate, endDate).buildQuery();
        } else {
            query = jqlClauseBuilder.createdBetween(startDate, endDate).and().project(projectId).buildQuery();
        }
        PagerFilter pagerFilter = PagerFilter.getUnlimitedFilter();

        SearchResults searchResults = null;
        try {
            searchResults = searchService.search(user, query, pagerFilter);
        } catch (SearchException e) {
            e.printStackTrace();
        }
        return searchResults != null ? searchResults.getIssues() : null;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String actionType = req.getParameter("actionType");

        if (actionType.equals("new")) {
            handleReportCreation(req, resp);
        } else {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private void handleReportCreation(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> context = new HashMap<>();

        String projectParam = req.getParameter("project");
        String userParam = req.getParameter("user");
        String dateStart = req.getParameter("dateStart");
        String dateEnd = req.getParameter("dateEnd");

        boolean errors = false;
        if (dateEnd == null || dateEnd.isEmpty()) {
            context.put("errors", Collections.singletonList("End date is required."));
            errors = true;
        }
        if (dateStart == null || dateStart.isEmpty()) {
            context.put("errors", Collections.singletonList("Start date is required."));
            errors = true;
        }
        if (userParam == null || userParam.isEmpty()) {
            context.put("errors", Collections.singletonList("User is required."));
            errors = true;
        }
        if (errors) {
            context.put("allUsers", getAllUsers());
            context.put("allProjects", getAllProjects());
            templateRenderer.render(NEW_REPORT_TEMPLATE, context, resp.getWriter());
            return;
        }

        ApplicationUser user = userManager.getUser(userParam);
        Project project = projectService.getProjectByKey(projectParam).getProject();

        List<Issue> issues = getIssues(user, dateStart, dateEnd, project == null ? null : project.getId());

        context.put("user", user);
        context.put("project", project);
        context.put("issues", issues);
        resp.setContentType("text/html;charset=utf-8");
        templateRenderer.render(GENERATED_REPORT_TEMPLATE, context, resp.getWriter());
    }

}