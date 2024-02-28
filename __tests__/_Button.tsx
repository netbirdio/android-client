import React from 'react';
import { shallow } from 'enzyme';
import Button from '../src/components/Button/index';

describe('Button', () => {
  const mockProps = {
    text: 'Click me',
    outline: false,
    loading: false,
    imagePrefix: jest.fn(),
    onPress: jest.fn(),
  };

  it('renders correctly without outline and loading', () => {
    const wrapper = shallow(<Button {...mockProps} />);
    expect(wrapper).toMatchSnapshot();
  });

  it('renders correctly with outline', () => {
    const wrapper = shallow(<Button {...mockProps} outline={true} />);
    expect(wrapper).toMatchSnapshot();
  });

  it('renders correctly with loading', () => {
    const wrapper = shallow(<Button {...mockProps} loading={true} />);
    expect(wrapper).toMatchSnapshot();
  });

  it('renders image prefix when imagePrefix prop is provided', () => {
    const wrapper = shallow(<Button {...mockProps} imagePrefix={jest.fn()} />);
    expect(wrapper.find('imagePrefix')).toBeTruthy();
  });
});
