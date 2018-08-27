import React, {Component} from 'react';
import PropTypes from 'prop-types';
import {connect} from 'react-redux';
import Spinner from '../spinner/spinner';
import FolderList from './folder-list';
import {resetFolderMessagesCache, updateFolderMessagesCache} from '../../services/message';
import {selectFolder, selectMessage} from '../../actions/application';
import styles from './folder-container.scss';
import mainCss from '../../styles/main.scss';

class FolderContainer extends Component {
  constructor(props) {
    super(props);
    this.abortControllerWrapper = {};
  }

  render() {
    return (
      <nav className={`${mainCss['mdc-list']}`}>
        <Spinner visible={this.props.activeRequests > 0 && this.props.folderList.length === 0}
          canvasClassName={styles.spinnerCanvas} />
        <FolderList folderList={this.props.folderList}
          selectedFolder={this.props.selectedFolder}
          onClickFolder={this.props.selectFolder.bind(this, this.abortControllerWrapper)} />
      </nav>
    );
  }
}

FolderContainer.propTypes = {
  activeRequests: PropTypes.number.isRequired,
  folderList: PropTypes.array.isRequired,
  selectFolder: PropTypes.func
};

const mapStateToProps = state => ({
  application: state.application,
  activeRequests: state.folders.activeRequests,
  selectedFolder: state.application.selectedFolder,
  folderList: state.folders.items,
  messages: state.messages
});

const mapDispatchToProps = dispatch => ({
  selectFolder: (abortControllerWrapper, folder, credentials, cachedFolderMessagesMap) => {
    dispatch(selectFolder(folder));
    dispatch(selectMessage(null));
    if (abortControllerWrapper && abortControllerWrapper.abortController) {
      abortControllerWrapper.abortController.abort();
    }
    abortControllerWrapper.abortController = new AbortController();
    // Performance: Perform an initial load of the latest (30*) messages in the folder
    const initialLoadMessageCount = 30;
    if (cachedFolderMessagesMap instanceof Map === false
      && folder.messageCount >= initialLoadMessageCount) {
      updateFolderMessagesCache(dispatch, credentials, folder, abortControllerWrapper.abortController.signal,
        folder.messageCount - initialLoadMessageCount, folder.messageCount);
    }
    resetFolderMessagesCache(dispatch, credentials, folder, abortControllerWrapper.abortController.signal);
  }
});

const mergeProps = (stateProps, dispatchProps, ownProps) => (Object.assign({}, stateProps, dispatchProps, ownProps, {
  selectFolder: (abortControllerWrapper, folder) =>
    dispatchProps.selectFolder(abortControllerWrapper, folder, stateProps.application.user.credentials,
      stateProps.messages.cache[folder.folderId])
}));

export default connect(mapStateToProps, mapDispatchToProps, mergeProps)(FolderContainer);
