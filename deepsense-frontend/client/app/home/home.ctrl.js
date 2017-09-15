'use strict';

/* @ngInject */
function Home($rootScope, $uibModal, $state, WorkflowService, NotificationService,
              WorkflowsApiClient, PageService, ConfirmationModalService, config) {

  this.init = () => {
    PageService.setTitle('Home');
    $rootScope.stateData.dataIsLoaded = true;
    this.canShowWorkflows = false;
    this.icon = '';
    this.info = '';
    this.sort = {
      column: 'updated',
      descending: true
    };

    this.downloadWorkflows();
  };

  this.downloadWorkflows = () => {
    this.canShowWorkflows = false;
    WorkflowService.downloadWorkflows().then(() => {
      this.workflows = WorkflowService.getAllWorkflows();
      if (this.workflows && this.workflows.length === 0) {
        this.setWarning('fa-ban', 'No workflows found!');
      } else {
        this.canShowWorkflows = true;
      }
    }, () => {
      this.setWarning('fa-exclamation-circle', 'Could not connect to the database. Try to reload the page.');
    });
  };

  this.setWarning = (icon, information) => {
    this.canShowWorkflows = false;
    this.icon = icon;
    this.info = information;
  };

  this.getTriggerEventBasedOnDescriptionLength = (description) => {
    return description.length > 50 ? 'mouseenter' : 'none';
  };

  this.sortBy = (columnName) => {
    if (this.sort.column === columnName) {
      this.sort.descending = !this.sort.descending;
    } else {
      this.sort.column = columnName;
      this.sort.descending = false;
    }
  };

  this.isWorkflowLoading = () => {
    return WorkflowService.isWorkflowLoading();
  };

  this.getClass = (columnName) => {
    if (this.sort.column === columnName) {
      let icon = 'glyphicon glyphicon-chevron';
      return this.sort.descending ? icon + '-down' : icon + '-up';
    }
  };

  this.getVersion = () => {
    return config.editorVersion;
  };

  this.getAllWorkflows = () => {
    return WorkflowService.getAllWorkflows();
  };

  this.goToWorkflowEditor = (workflowId) => {
    $state.go('workflows.editor', {id: workflowId});
  };

  this.cloneWorkflow = (workflowId) => {
    WorkflowsApiClient.cloneWorkflow(workflowId).then((response) => {
      this.downloadWorkflows();
    }, () => {
      NotificationService.showWithParams({
        message: 'There was an error during copying this workflow.',
        title: 'Workflow copy',
        settings: {timeOut: 10000},
        notificationType: 'error'
      });
    });
  };

  this.deleteWorkflow = function(workflow) {
    ConfirmationModalService.showModal({
      message: 'The operation will delete workflow "' + workflow.name + '". Deletion cannot be undone afterwards.'
    }).
    then(() => {
      WorkflowService.deleteWorkflow(workflow.id);
    });
  };

  this.displayCreateWorkflowPopup = (event) => {
    event.preventDefault();

    let modal = $uibModal.open({
      animation: true,
      templateUrl: 'app/common/modals/new-workflow-modal/new-workflow-modal.html',
      controller: 'NewWorkflowModalController as controller',
      backdrop: 'static',
      keyboard: true
    });

    modal.result.then((workflowId) => {
      $state.go('workflows.editor', {
        id: workflowId
      });
    });
  };

  this.displayUploadWorkflowPopup = (event) => {
    event.preventDefault();

    let modal = $uibModal.open({
      animation: true,
      templateUrl: 'app/common/modals/upload-workflow-modal/upload-workflow-modal.html',
      controller: 'UploadWorkflowModalController as controller',
      backdrop: 'static',
      keyboard: true
    });

    modal.result.then((workflowId) => {
      $state.go('workflows.editor', {
        'id': workflowId
      });
    });
  };

  this.displayUploadExecutionWorkflowPopup = (event) => {
    event.preventDefault();

    let modal = $uibModal.open({
      animation: false,
      templateUrl: 'app/common/modals/upload-execution-report-modal/upload-execution-report-modal.html',
      controller: 'UploadWorkflowExecutionReportModalController as controller',
      backdrop: 'static',
      keyboard: true
    });

    modal.result.then((reportId) => {
      $state.go('workflows.report', {
        'reportId': reportId
      });
    });
  };

  this.init();
}

exports.function = Home;

exports.inject = function(module) {
  module.controller('Home', Home);
};
