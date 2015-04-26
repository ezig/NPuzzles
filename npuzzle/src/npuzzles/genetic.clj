(ns npuzzles.genetic
	(:use [npuzzles.puzzle])
	(:use [clojure.set :only (intersection)]))

(defrecord Gene [puzzle prev-dir])

;PUBLIC FUNCTIONS

(declare run-phase)
(defn solve
	"Given a puzzle, runs the genetic algorithm with specified number of
	 generations and phases until either a solution is found,
	 in which case the chromosome that solves the puzzle is returned,
	 or Nil if a solution is not found within the specified limits"
	[puzzle phases generations]
	(loop [n phases
		   puz puzzle
		   solution []]
			(if (= n 0)
				nil
				(let [best-chrom (run-phase puzzle (:rows puz) generations)
					  best-gene (last best-chrom)
					  new-solution (concat solution (drop 1 best-chrom))]
					(if (solved? (:puzzle best-gene))
						new-solution
						(recur (- n 1) (:puzzle best-gene) new-solution))))))

; PRIVATE FUNCTIONS
; TODO: PRIVATIZE THESE WHEN DONE TESTING
(defn random-dir
	"Given a puzzle, returns a random valid direction that is NOT dir
	Based on the assumption that the puzzle has 2 valid moves in every state"
 	[puzzle dir]
 	(let [valid (filter #(not (= % dir)) (valid-directions puzzle))]
 		(rand-nth valid)))

(defn opposite-dir
	"Given a direction, returns the opposite"
	 [dir]
	 (case dir
	 	:up :down
	 	:down :up
	 	:left :right
	 	:right :left
	 	nil))

(defn fitness
	"Given a chromosome, determines the fitness
	(right now, this is just the manhattan-distance of the last gene)"
	[chromosome]
	(manhattan-distance (:puzzle (last chromosome))))

(defn next-gene
	"Given a gene, returns a gene that represents a possible next state
	The new gene will be a valid state within one move of the original gene
	and will NOT undo the previous move"
	[{puzzle :puzzle prev-dir :prev-dir}]
	(let [next-dir (random-dir puzzle (opposite-dir prev-dir))]
		{:puzzle (slide puzzle next-dir) :prev-dir next-dir}))

(defn gen-chromosome
	"Given a starting puzzle state, make moves number of random (valid) moves
	to build a list of genes"
	[length]
	(into [] (map (fn [x] (rand)) (range 0 length))))

(defn gen-population
	"Given an initial puzzle and a pop-size and chromosome size, creates
	list of random chromosomes"
	[pop-size chrom-size]
	(into [] (for [x (range pop-size)]
		(generate-chromosome chrom-size))))

(defn mutate
	"Given a chromosome, extend the chromosome create another valid puzzle
	by moving the current puzzle. If this results in a chromosome beyond
	max-size, the chromosome will be truncated randomly"
	[chromosome]
	(assoc chromosome (rand-int (+ (count chromosome) -1)) (rand)))

(defn interpret-chromsome
	"Given a chromosome and a puzzle, returns a the final puzzle
	and a vector of directions"
	[chromosome puzzle]
	(loop [chrom chromosome
		   puz puzzle
		   dir-list []]
		(if (empty? chrom)
			[puz, dir-list]
			(let [valid-dirs (valid-directions puz)
				  next-dir (valid-dirs (int (* (chrom 0) (count valid-dirs))))
				  new-chrom (into [] (drop 1 chrom))]
				  ; next-dir :right]
				(recur new-chrom (slide puz next-dir) (conj dir-list next-dir))))))

(defn rand-intersection
	"Given two chromosomes, returns a vector of two indices representing the
	positions of a random puzzle shared in both chromosomes"
	[chromosome1 chromosome2]
	(let [puzzles1 (map #(:puzzle %) chromosome1)
		  puzzles2 (map #(:puzzle %) chromosome2)
		  inter-puzzle (rand-nth (into [] (intersection (set puzzles1) (set puzzles2))))]
		[(.indexOf puzzles1 inter-puzzle ) (.indexOf puzzles2 inter-puzzle)]))

(defn crossover
	"Given two chromosomes, finds the points in the chromosomes where
	 the puzzle states are the same, picks a random point from this list,
	 and returns a new chromosome that takes the leading part of the first
	 and concats it with the trailing part of the second chromosome."
	[chromosome1 chromosome2]
	(let [indices (rand-intersection chromosome1 chromosome2)]
		(into [] (concat (take (indices 0) chromosome1) (drop (indices 1) chromosome2)))))

(defn n-best
	"Given a population, returns the n most fit chromosomes"
	[population n]
	(take n (sort-by fitness population)))

(defn run-generation
	"Given a population, the number of best to pick from the generation,
	the number of crossover chromosomes to generation, the mutation probability
	(int out of 100), the max size of the individual chromosomes returns a
	new population sorted by fitness"
	[population num-best num-cross mut-prob max-size]
	(let [best (n-best population num-best)
		  crossovers (repeatedly num-cross #(crossover (rand-nth best) (rand-nth best)))
		  new-pop (concat best crossovers)
		  mut-pop (map #(if (< (rand-int 100) mut-prob) (mutate % max-size) %) new-pop)]
		  (vec (sort-by fitness mut-pop))))

(defn run-phase
	"Given a starting puzzle state, generates a population of pop-size
	 chromosomes and then runs num-gens number of generations within the phase.
	 run-phase will return a solution as soon as it finds one, otherwise
	 return the best chromosome from the phase"
	[puzzle pop-size num-gens]
	(let [population (generate-population puzzle pop-size (:rows puzzle))
		  num-best (* 2 (:rows puzzle))
		  num-cross num-best
		  mut-prob 50
		  max-size (* 20 (:rows puzzle))]
		(loop [n num-gens
			   p population]
			(if (= n 0)
				(first p)
				(let [new-pop (run-generation p num-best num-cross mut-prob max-size)
					  best (first new-pop)]
					(if (solved? (:puzzle (last best)))
						(first new-pop)
						(recur (- n 1) new-pop)))))))