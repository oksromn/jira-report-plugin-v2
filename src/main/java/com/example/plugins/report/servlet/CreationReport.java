package com.example.plugins.report.servlet;

import com.atlassian.jira.bc.ServiceOutcome;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.bc.project.ProjectService;
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
    private static final String PERSONAL_REPORT_TEMPLATE = "/templates/personal-report.vm";
    private static final String PROJECT_TIME_REPORT = "/templates/project-time.vm";
    private static final String GENERAL_PROJECT_REPORT = "/templates/general-report.vm";

    public CreationReport(ProjectService projectService,
                          SearchService searchService,
                          TemplateRenderer templateRenderer,
                          JiraAuthenticationContext authenticationContext,
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

    private List<Issue> getIssues(String type, ApplicationUser user, String startDate, String endDate, Long projectId) {
        JqlClauseBuilder jqlClauseBuilder = JqlQueryBuilder.newClauseBuilder();
        Query query;

        if (type.equals("PersonalReport")) {
            if (projectId == null) {
                query = jqlClauseBuilder.createdBetween(startDate, endDate).and().assigneeUser(user.getName()).and().status("DONE").buildQuery();
            } else {
                query = jqlClauseBuilder.createdBetween(startDate, endDate).and().assigneeUser(user.getName()).and().project(projectId).and().status("DONE").buildQuery();
            }
        } else if (type.equals("ProjectTimeReport")) {
            query = jqlClauseBuilder.createdBetween(startDate, endDate).and().project(projectId).and().status("DONE").buildQuery();
        } else {
            query = jqlClauseBuilder.createdBetween(startDate, endDate).and().status("DONE").buildQuery();
        }

        PagerFilter pagerFilter = PagerFilter.getUnlimitedFilter();

        SearchResults searchResults = null;
        try {
            if (user == null) {
                searchResults = searchService.search(authenticationContext.getLoggedInUser(), query, pagerFilter);
            } else {
                searchResults = searchService.search(user, query, pagerFilter);
            }
        } catch (SearchException e) {
            e.printStackTrace();
        }
        return searchResults != null ? searchResults.getIssues() : null;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String actionType = req.getParameter("actionType");

        if (actionType.equals("new")) {
            handlePersonalReport(req, resp);
        } else if (actionType.equals("projectTime")) {
            handleProjectTimeReport(req, resp);
        } else if (actionType.equals("generalReport")) {
            handleGeneralReport(req, resp);
        } else {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private void handlePersonalReport(HttpServletRequest req, HttpServletResponse resp) throws IOException {
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

        List<Issue> issues = getIssues("PersonalReport", user, dateStart, dateEnd, project == null ? null : project.getId());

        context.put("user", user);
        context.put("project", project);
        context.put("issues", issues);
        resp.setContentType("text/html;charset=utf-8");
        templateRenderer.render(PERSONAL_REPORT_TEMPLATE, context, resp.getWriter());
    }

    private void handleProjectTimeReport(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> context = new HashMap<>();

        String projectParam = req.getParameter("project");
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
        if (projectParam == null || projectParam.isEmpty()) {
            context.put("errors", Collections.singletonList("Project is required."));
            errors = true;
        }
        if (errors) {
            context.put("allUsers", getAllUsers());
            context.put("allProjects", getAllProjects());
            templateRenderer.render(NEW_REPORT_TEMPLATE, context, resp.getWriter());
            return;
        }

        Project project = projectService.getProjectByKey(projectParam).getProject();
        List<Issue> issues = getIssues("ProjectTimeReport", null, dateStart, dateEnd, project.getId());

        context.put("project", project);
        context.put("issues", issues);
        resp.setContentType("text/html;charset=utf-8");
        templateRenderer.render(PROJECT_TIME_REPORT, context, resp.getWriter());
    }

    private void handleGeneralReport(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> context = new HashMap<>();

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
        if (errors) {
            context.put("allUsers", getAllUsers());
            context.put("allProjects", getAllProjects());
            templateRenderer.render(NEW_REPORT_TEMPLATE, context, resp.getWriter());
            return;
        }

        List<Issue> allIssues = getIssues("GeneralReport", null, dateStart, dateEnd, null);
        Map<String, Map<String, Long>> issues = new HashMap<>();

        assert allIssues != null;
        for (Issue issue : allIssues) {
            String name = issue.getAssignee().getName();
            String project = issue.getProjectObject().getName();
            Long timeSpent = issue.getTimeSpent();

            if (issues.containsKey(project)) {
                if (issues.get(project).get(name) == null) {
                    issues.get(project).put(name, timeSpent);
                } else {
                    Long oldValue = issues.get(project).get(name);
                    Long value = oldValue + timeSpent;
                    issues.get(project).put(name, value);
                }
            } else {
                Map<String, Long> tempData = new HashMap<>();
                tempData.put(name, issue.getTimeSpent());
                issues.put(project, tempData);
            }
        }

        context.put("issues", issues);
        resp.setContentType("text/html;charset=utf-8");
        templateRenderer.render(GENERAL_PROJECT_REPORT, context, resp.getWriter());
    }
}