'use strict';

import DatasourcesToolbarTemplate from './datasources-toolbar.html';

import {datasourceContext} from 'APP/enums/datasources-context.js';

// Assets
import './datasources-toolbar.less';
import './modals/datasources-modals.less';
import './modals/modal-footer/modal-footer.less';
import './modals/external-file-modal/external-file-modal.less';
import './modals/database-modal/database-modal.less';
import './modals/hdfs-modal/hdfs-modal.less';
import './modals/library-modal/library-modal.less';

const DatasourcesToolbarComponent = {
  bindings: {
    context: '<'
  },
  templateUrl: DatasourcesToolbarTemplate,
  controller: class DatasourcesToolbarController {
    constructor($scope, DatasourcesModalsService, LibraryService) {
      'ngInject';

      this.DatasourcesModalsService = DatasourcesModalsService;
      this.datasourceContext = datasourceContext;

      $scope.$watch(LibraryService.isUploadingInProgress, (newValue) => {
        this.uploadingInProgress = newValue;
      }, true);
    }

    openModal(type) {
      this.DatasourcesModalsService.openModal(type);
    }
  }

};

export default DatasourcesToolbarComponent;
