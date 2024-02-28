import React from 'react';
import { shallow } from 'enzyme';
import ListPeers from '../src/components/ListPeers/index'; 
import { useSelector } from 'react-redux';

// Create a mock for the useSelector hook
jest.mock('react-redux', () => ({
  useSelector: jest.fn(),
}));

// Mock the Dimensions module
jest.mock('react-native/Libraries/Utilities/Dimensions', () => ({
  get: () => ({ width: 400, height: 800 }),
}));

interface Props {
    bottomSheet: boolean,
    onClose: jest.Mock,
}

describe('ListPeers component', () => {
  // Define the default props
  const defaultProps: Props = {
    bottomSheet: true,
    onClose: jest.fn(),
  };

  // Helper function to create a shallow wrapper with the provided props
  const createWrapper = (props: Partial<Props> = {}) => {
    const finalProps = { ...defaultProps, ...props };
    return shallow(<ListPeers {...finalProps} />);
  };

  it('should render without crashing', () => {
    const wrapper = createWrapper();
    expect(wrapper.exists()).toBe(true);
  });

  it('should render the correct number of peers', () => {
    const mockPeers = [
      { domainName: 'Peer 1', ip: '192.168.0.1', connStatus: 'connected' },
      { domainName: 'Peer 2', ip: '192.168.0.2', connStatus: 'disconnected' },
    ];
    // Mock the useSelector hook to return the mockPeers
    (useSelector as jest.Mock).mockReturnValueOnce({ data: mockPeers, total: 2, connected: 1 });

    const wrapper = createWrapper();
    expect(wrapper).toMatchSnapshot()
  });
});

