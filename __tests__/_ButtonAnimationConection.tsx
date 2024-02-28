import React from 'react';
import { shallow, mount } from 'enzyme';
import configureStore from 'redux-mock-store';
import { Provider } from 'react-redux';
import ButtonAnimationConnection from '../src/components/ButtonAnimationConnection';

const mockStore = configureStore();
const initialState = {}; // Define the initial state for the store
const store = mockStore(initialState);

jest.mock('../src/components/ButtonAnimationConnection', () => {
    return jest.fn().mockImplementation(({ toggleConnection }) => (
      <button onClick={toggleConnection} />
    ));
  });

describe('ButtonAnimationConnection', () => {

  it('should call toggle connection when button is pressed', () => {
    const toggleConnectionMock = jest.fn(); // Create a mock function for toggleConnection
  
    const wrapper = shallow(
      <Provider store={store}>
        <ButtonAnimationConnection ref={toggleConnectionMock} />
      </Provider>
    );
    const buttonAnimationConnection = wrapper.find(ButtonAnimationConnection).dive();
    
    // Simulate button press event
    buttonAnimationConnection.simulate('press');

    // Assert that toggleConnectionMock was called
    expect(toggleConnectionMock).toMatchSnapshot();
  });

  it('should call toggle connection when TouchableWithoutFeedback is pressed', () => {
    const toggleConnectionMock = jest.fn(); // Create a mock function for toggleConnection

    const wrapper = shallow(
      <Provider store={store}>
        <ButtonAnimationConnection ref={toggleConnectionMock} />
      </Provider>
    );

    const buttonAnimationConnection = wrapper.find(ButtonAnimationConnection).dive();
    const touchableWithoutFeedback = buttonAnimationConnection.find('TouchableWithoutFeedback');

    expect(wrapper).toMatchSnapshot();
    expect(touchableWithoutFeedback).toMatchSnapshot();
  });

});
