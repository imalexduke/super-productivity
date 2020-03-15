import {DropListModelSource} from '../../tasks/task.model';
import {filterOutId} from '../../../util/filter-out-id';

export const moveTaskForWorkContextLikeState = (
  taskId: string,
  newOrderedIds: string[],
  target: DropListModelSource,
  taskIdsBefore: string[]
): string[] => {
  const idsFilteredMoving = taskIdsBefore.filter(filterOutId(taskId));
  // NOTE: move to end of complete list for done tasks
  const emptyListVal = (target === 'DONE')
    ? idsFilteredMoving.length
    : 0;
  return moveItemInList(taskId, idsFilteredMoving, newOrderedIds, emptyListVal);
};

// TODO I don't understand this anymore...
export const moveItemInList = (itemId: string, completeList: string[], partialList: string[], emptyListVal = 0): string[] => {
  let newIndex;

  const curInUpdateListIndex = partialList.indexOf(itemId);
  const prevItemId = partialList[curInUpdateListIndex - 1];
  const nextItemId = partialList[curInUpdateListIndex + 1];
  const newCompleteList = completeList.filter((id) => id !== itemId);

  if (!itemId) {
    throw new Error('Drop Model Error');
  } else if (prevItemId) {
    newIndex = newCompleteList.indexOf(prevItemId) + 1;
  } else if (nextItemId) {
    newIndex = newCompleteList.indexOf(nextItemId);
  } else if (partialList.length === 1) {
    newIndex = emptyListVal;
  } else {
    throw new Error('Drop Model Error');
  }

  newCompleteList.splice(newIndex, 0, itemId);
  return newCompleteList;
};
