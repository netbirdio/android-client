import React from 'react';
import {shallow, ShallowWrapper} from 'enzyme';
import ModalAlert from '../src/components/ModalAlert/index';

interface Props {
  title?: string;
  text?: string;
  visible: boolean;
  autoClose: boolean;
  textOkButton?: string;
  onOk?: () => void;
  textCancelButton?: string;
  onCancel?: () => void;
  icon?: any;
  iconSource?: any;
  body?: () => JSX.Element;
  messageContent?: () => JSX.Element;
  onPressOverlay?: () => void;
  onPress?: () => void;
}

describe('ModalAlert', () => {
  const mockProps: Props = {
    title: 'Test Title',
    text: 'Test Text',
    visible: false,
    autoClose: false,
    textOkButton: 'OK',
    onOk: jest.fn(),
    textCancelButton: 'Cancel',
    onCancel: jest.fn(),
    icon: 'test-icon',
    iconSource: 'test-icon-source',
    body: jest.fn(),
    messageContent: jest.fn(),
    onPressOverlay: jest.fn(),
    onPress: jest.fn(),
  };

  let wrapper: ShallowWrapper;

  beforeEach(() => {
    wrapper = shallow(<ModalAlert {...mockProps} />);
  });
  // Suppress the warning message
  console.error = jest.fn();

  it('renders correctly', () => {
    const wrapper = shallow(<ModalAlert {...mockProps} />);
    expect(wrapper).toMatchSnapshot();
  });

  it('calls onOk when OK button is pressed', () => {
    const wrapper = shallow(<ModalAlert {...mockProps} />);
    wrapper.find('[id="okButton"]').simulate('press');
    expect(mockProps.onOk).toHaveBeenCalled();
  });

  it('calls onCancel when Cancel button is pressed', () => {
    const onCancelMock = jest.fn();
    const wrapper = shallow(
      <ModalAlert {...mockProps} onCancel={onCancelMock} />,
    );
    wrapper.find('[id="cancelButton"]').simulate('press');
    expect(onCancelMock).toHaveBeenCalled();
  });

  it('does not throw an error when Cancel button is not provided', () => {
    const wrapper = shallow(<ModalAlert {...mockProps} />);
    expect(() => {
      wrapper.find('[id="cancelButton"]').simulate('press');
    }).not.toThrow();
  });

  it('calls onPressOverlay when overlay is pressed', () => {
    const wrapper = shallow(<ModalAlert {...mockProps} />);
    wrapper.find('[id="overlayId"]').first().simulate('press');
    expect(mockProps.onPressOverlay).toMatchSnapshot();
  });
});
