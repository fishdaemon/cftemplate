module Kernel
  # The kernel select method overrides the FN::select method.
  # Kernel select is unlikely to be needed.
  remove_method :select
end

class << Kernel
  # The kernel select method overrides the FN::select method.
  # Kernel select is unlikely to be needed.
  remove_method :select
end

# CloudFormation intrinsic template functions.
# @see http://docs.amazonwebservices.com/AWSCloudFormation/latest/UserGuide/intrinsic-function-reference.html Intrinsic Function Reference
module FN
  # Generate a Fn::Select function call.
  #
  # @param index index of the object to retrieve
  # @param list list of objects to select from
  # @return [Hash] { 'Fn::Select' => [index, list] }
  def select(index, *list)
    if list.length == 1
      if list[0].is_a? Array
        {'Fn::Select' => [index, list[0].flatten(1)]}
      else
        {'Fn::Select' => [index, list[0]]}
      end
    else
      {'Fn::Select' => [index, list.flatten(1)]}
    end
  end

  module_function :select

  # Generate a Fn::GetAZs function call.
  #
  # @param region name of the region to get the availability zones for or
  #               'AWS::Region' to get availability zones in the region the stack was created
  # @return [Hash] { 'Fn::GetAZs' => [index, list] }
  def get_azs(region=Aws::REGION)
    {'Fn::GetAZs' => region}
  end

  module_function :get_azs

  # Generate a Ref function call.
  #
  # @param name name of the object/value to reference
  # @return [Hash] { "Ref" => name }
  def ref(name)
    {'Ref' => name}
  end

  module_function :ref

  # Generate a Fn::FindInMap function call.
  #
  # @param map name of the mapping
  # @param key name of the key in the mapping
  # @param value name of the value
  # @return [Hash] { "Fn::FindInMap" => [map, key, value] }
  def find_in_map(map, key, value)
    {'Fn::FindInMap' => [map, key, value]}
  end

  module_function :find_in_map

  # Generate a Fn::GetAtt function call.
  #
  # @param resource name of the resource to get the attribute from
  # @param attribute name of the attribute to get
  # @return [Hash] { "Fn::GetAtt" => [resource, attribute] }
  def get_att(resource, attribute)
    {'Fn::GetAtt' => [resource, attribute]}
  end

  module_function :get_att

  # Generate a Fn::Base64 function call.
  #
  # @param content content to base64
  # @return [Hash] { "Fn::Base64" => content }
  def base64(content)
    {'Fn::Base64' => content}
  end

  module_function :base64

  # Generate a Fn::Join function call.
  #
  # @param [String] separator separator to place between content values
  # @param [Array] content content values to join
  # @return [Hash] { "Fn::Join" => [separator, content] }
  def join(separator, *content)
    if content.length == 1
      if content[0].is_a? Array
        {'Fn::Join' => [separator, content[0].flatten(1)]}
      else
        {'Fn::Join' => [separator, content[0]]}
      end
    else
      {'Fn::Join' => [separator, content.flatten(1)]}
    end
  end

  module_function :join
end