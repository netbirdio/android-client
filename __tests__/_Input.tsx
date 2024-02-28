import React from 'react';
import {shallow} from 'enzyme';
import Input from '../src/components/Input/index';

interface Props {
  onChange?: jest.Mock;
  onChangeText?: jest.Mock;
  onTextInput?: jest.Mock;
  styles?: any; 
  stylesMask?: any;
  editable?: boolean;
  value?: string;
  placeHolder?: string;
  keyboardType?: string;
  error?: boolean;
  errorMessage?: string;
  clearVisible?: boolean;
  onClear?: jest.Mock;
}

describe('Input component', () => {
  const defaultProps: Props = {
    onChange: jest.fn(),
    onChangeText: jest.fn(),
    onTextInput: jest.fn(),
    styles: undefined,
    stylesMask: undefined,
    editable: true,
    value: '',
    placeHolder: 'Enter text',
    keyboardType: 'default',
    error: false,
    errorMessage: '',
    clearVisible: false,
    onClear: jest.fn(),
  };

  it('should render correctly with default props', () => {
    const wrapper = shallow(<Input {...defaultProps} />);
    expect(wrapper).toMatchSnapshot();
  });

});
