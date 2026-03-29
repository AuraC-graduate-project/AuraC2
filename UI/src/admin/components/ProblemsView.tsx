import { useState, useEffect, useCallback } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from './ui/card';
import { Button } from './ui/button';
import { Badge } from './ui/badge';
import { Plus, ChevronRight } from 'lucide-react';
import { CreateProblemModal } from './CreateProblemModal';
import { TestCasesPanel } from './TestCasesPanel';
import { getProblem, getProblemsByContest } from '../services/api';
import { ProblemResponse } from '../types/api';
import { toast } from 'sonner';

interface ProblemsViewProps {
  contestId: number | null;
}

export function ProblemsView({ contestId }: ProblemsViewProps) {
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [problems, setProblems] = useState<ProblemResponse[]>([]);
  const [selectedProblem, setSelectedProblem] = useState<ProblemResponse | null>(null);
  const [isLoadingProblem, setIsLoadingProblem] = useState(false);
  const [isLoadingList, setIsLoadingList] = useState(false);

  const loadProblems = useCallback(async () => {
    if (!contestId) return;
    setIsLoadingList(true);
    try {
      const list = await getProblemsByContest(contestId);
      setProblems(list);
      // Keep selection stable if possible
      if (selectedProblem) {
        const stillExists = list.find(p => p.id === selectedProblem.id);
        if (!stillExists) setSelectedProblem(null);
      }
    } catch (error) {
      console.error('Failed to load problems list:', error);
      toast.error('Failed to load problems list');
      setProblems([]);
    } finally {
      setIsLoadingList(false);
    }
  }, [contestId, selectedProblem]);

  useEffect(() => {
    loadProblems();
  }, [loadProblems]);

  // Load problem details when selected
  const handleProblemSelect = async (problem: ProblemResponse) => {
    setIsLoadingProblem(true);
    try {
      // Fetch fresh problem data from backend
      const problemData = await getProblem(problem.id);
      setSelectedProblem(problemData);
    } catch (error) {
      console.error('Failed to load problem:', error);
      toast.error('Failed to load problem details');
      setSelectedProblem(problem); // Fallback to cached data
    } finally {
      setIsLoadingProblem(false);
    }
  };

  const handleProblemCreated = async (newProblem: ProblemResponse) => {
    // Auto-select newly created problem, then reload list from backend
    setSelectedProblem(newProblem);
    await loadProblems();
  };

  if (!contestId) {
    return (
      <Card className="border border-gray-200 shadow-sm">
        <CardHeader className="bg-[#1E293B] text-white">
          <CardTitle>Problems</CardTitle>
        </CardHeader>
        <CardContent className="p-6">
          <p className="text-slate-600 py-8 text-center">
            No active contest. Please select a contest first.
          </p>
        </CardContent>
      </Card>
    );
  }

  return (
    <>
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Problems List */}
        <Card className="border border-gray-200 shadow-sm lg:col-span-1">
          <CardHeader className="bg-[#1E293B] text-white">
            <div className="flex items-center justify-between">
              <CardTitle>Problems</CardTitle>
              <Button 
                size="sm"
                className="gap-2"
                onClick={() => setCreateModalOpen(true)}
              >
                <Plus className="w-4 h-4" />
                Create
              </Button>
            </div>
          </CardHeader>
          <CardContent className="p-4">
            {isLoadingList ? (
              <div className="text-center py-8">
                <p className="text-slate-600 text-sm">Loading problems...</p>
              </div>
            ) : problems.length === 0 ? (
              <div className="text-center py-8">
                <p className="text-slate-600 text-sm mb-2">
                  No problems yet
                </p>
                <p className="text-slate-500 text-xs">
                  Use "Create" to add problems.
                </p>
              </div>
            ) : (
              <div className="space-y-2">
                {problems.map((problem) => (
                  <button
                    key={problem.id}
                    onClick={() => handleProblemSelect(problem)}
                    className={`w-full text-left p-3 rounded-lg border transition-colors ${
                      selectedProblem?.id === problem.id
                        ? 'bg-slate-100 border-slate-300'
                        : 'bg-white border-gray-200 hover:bg-gray-50'
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <div className="flex-1 min-w-0">
                        <p className="text-sm text-slate-800 truncate">
                          {problem.title}
                        </p>
                        <div className="flex items-center gap-2 mt-1">
                          <Badge 
                            variant={
                              problem.difficulty === 'EASY' 
                                ? 'default' 
                                : problem.difficulty === 'MEDIUM'
                                ? 'secondary'
                                : 'destructive'
                            }
                            className="text-xs"
                          >
                            {problem.difficulty}
                          </Badge>
                        </div>
                      </div>
                      <ChevronRight className="w-4 h-4 text-slate-400 flex-shrink-0 ml-2" />
                    </div>
                  </button>
                ))}
              </div>
            )}
          </CardContent>
        </Card>

        {/* Problem Details */}
        <Card className="border border-gray-200 shadow-sm lg:col-span-2">
          <CardHeader className="bg-[#1E293B] text-white">
            <CardTitle>Problem Details</CardTitle>
          </CardHeader>
          <CardContent className="p-6">
            {isLoadingProblem ? (
              <p className="text-slate-600 py-8 text-center">
                Loading problem details...
              </p>
            ) : !selectedProblem ? (
              <p className="text-slate-600 py-8 text-center">
                Select a problem from the list to view details and manage test cases
              </p>
            ) : (
              <div className="space-y-4">
                <div>
                  <h3 className="text-slate-800 mb-2">{selectedProblem.title}</h3>
                  <div className="flex items-center gap-2 mb-4">
                    <Badge 
                      variant={
                        selectedProblem.difficulty === 'EASY' 
                          ? 'default' 
                          : selectedProblem.difficulty === 'MEDIUM'
                          ? 'secondary'
                          : 'destructive'
                      }
                    >
                      {selectedProblem.difficulty}
                    </Badge>
                    <span className="text-sm text-slate-600">
                      Time: {selectedProblem.timeLimit}ms
                    </span>
                    <span className="text-sm text-slate-600">
                      Memory: {selectedProblem.memoryLimit}MB
                    </span>
                  </div>
                </div>

                <div>
                  <h4 className="text-slate-700 mb-2">Description</h4>
                  <div className="bg-gray-50 border border-gray-200 rounded-lg p-4">
                    <p className="text-slate-600 whitespace-pre-wrap">
                      {selectedProblem.description}
                    </p>
                  </div>
                </div>
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      {/* Test Cases Panel - Only shown when a problem is selected */}
      {selectedProblem && (
        <TestCasesPanel 
          problemId={selectedProblem.id} 
          problemTitle={selectedProblem.title}
        />
      )}

      <CreateProblemModal 
        open={createModalOpen}
        onOpenChange={setCreateModalOpen}
        contestId={contestId}
        onSuccess={handleProblemCreated}
      />
    </>
  );
}
