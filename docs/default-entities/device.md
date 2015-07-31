# Device

<!-- DO NOT EDIT THIS GENERATED FILE -->

<table class='usergrid-table entities-table'>
  <tr>
    <th>uuid</th>
    <th>UUID</th>
    <th>Unique entity ID</th>
  </tr>
  <tr class='ug-even usergrid-table'>
    <td>type</td>
    <td>string</td>
    <td>Type of entity, in this case 'device'</td>
  </tr>
  <tr class='ug-odd usergrid-table'>
    <td>name</td>
    <td>string</td>
    <td>Optional. Device name</td>
  </tr>
  <tr class='ug-even usergrid-table'>
    <td>created</td>
    <td>long</td>
    <td>UTC timestamp in milliseconds of when the entity was created</td>
  </tr>
  <tr class='ug-odd usergrid-table'>
    <td>modified</td>
    <td>long</td>
    <td>UTC timestamp in milliseconds of when the entity was last modified</td>
  </tr>
  <tr class='ug-even usergrid-table'>
    <td>metadata</td>
    <td>object</td>
    <td>A nested, JSON-formatted object that provides the relative path to the device entity, as well as additional data entities associated with the user. The following properties are included in metadata: path: Path to retrieve the device entity, including the device UUID collections: Nested object that contains paths to data entity collections associated with the device. receipts: Receipt entities associated with the device users: User entities associated with the device</td>
  </tr>
</table>